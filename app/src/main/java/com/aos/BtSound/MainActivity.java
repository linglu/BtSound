package com.aos.BtSound;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.aos.BtSound.contact.ObtainContactsUtil;
import com.aos.BtSound.log.DebugLog;
import com.aos.BtSound.preference.Config;
import com.aos.BtSound.preference.Settings;
import com.aos.BtSound.setting.IatSettings;
import com.aos.BtSound.util.JsonParser;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;
import com.iflytek.cloud.util.ContactManager;
import com.iflytek.cloud.util.ContactManager.ContactListener;
import com.umeng.update.UmengUpdateAgent;

import cn.yunzhisheng.common.USCError;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizer;
import cn.yunzhisheng.wakeup.basic.WakeUpRecognizerListener;

public class MainActivity extends Activity implements OnClickListener {
    private Context mContext = null;
    private Button mBtnTakeCall = null;
    private Button mBtnSendMessages = null;
    private Button mBtnNavigation = null;
    private Button mBtnWeb = null;
    private Button mBtnSettings = null;
    private EditText mEdtTransformResult = null;
    private Handler mHandler = new Handler();

    private SpeechRecognizer mSpeechRecognizer = null;// 语音对象
    private SharedPreferences mSharedPreferences = null;// 存储对象
    private AudioManager mAudioManager = null;// 音频管理类
    private BluetoothAdapter mBluetoothAdapter = null;// 蓝牙适配器
    private RecognizerDialog mRecognizerDialog = null;// 语音对话框
    private String mSmsBody = null;// 短信内容
    private SoundPool soundPool = null;
    private boolean mIsStarted = false;
    private boolean mIsFirstTime = false;// 是否第一次启动
    private WakeUpRecognizer mWakeUpRecognizer = null;// 唤醒对象
    private Vibrator mVibrator = null;// 唤醒震动
    private boolean isReceivered = false;// 广播标识

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
        mContext = this;
        Intent in = getIntent();
        if (in != null && in.getExtras() != null) {
            mIsFirstTime = in.getExtras().getBoolean(Config.IS_FIRST_TIME);
        }
        initView();
        setListener();
        // 获取联系人信息
        ObtainContactsUtil.getInstance(mContext).getPhoneContacts();
        if (!mIsFirstTime)
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    blePrepare();
                }
            }, 1000);
        initWakeUp();
        wakeUpStart();
        // 友盟更新初始化
        UmengUpdateAgent.update(this);
    }

    private void initView() {
        mBtnTakeCall = (Button) findViewById(R.id.btn_take_call);
        mBtnSendMessages = (Button) findViewById(R.id.btn_send_messages);
        mBtnNavigation = (Button) findViewById(R.id.btn_navigation);
        mBtnWeb = (Button) findViewById(R.id.btn_web);
        mBtnSettings = (Button) findViewById(R.id.settings);
        mEdtTransformResult = (EditText) findViewById(R.id.edt_result_text);
        // 初始化语音模块
        mSpeechRecognizer = SpeechRecognizer.createRecognizer(this, mInitListener);
        mRecognizerDialog = new RecognizerDialog(this, mInitListener);
        mSharedPreferences = getSharedPreferences(IatSettings.PREFER_NAME, Activity.MODE_PRIVATE);
        // 第一次上传联系人
        if (Settings.getBoolean(Config.IS_FIRST_TIME, true, false)) {
            ContactManager mgr = ContactManager.createManager(mContext, mContactListener);
            mgr.asyncQueryAllContactsName();
        }
    }

    private void setListener() {
        mBtnTakeCall.setOnClickListener(this);
        mBtnSendMessages.setOnClickListener(this);
        mBtnNavigation.setOnClickListener(this);
        mBtnWeb.setOnClickListener(this);
        mBtnSettings.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_take_call:
                showSpeakDialog();
                break;
            case R.id.btn_send_messages:
                showSpeakDialog();
                break;
            case R.id.btn_navigation:
                Toast.makeText(MainActivity.this, "敬请期待", Toast.LENGTH_SHORT).show();
                break;
            case R.id.btn_web:
                openWeb();
                break;
            case R.id.settings:
                Toast.makeText(MainActivity.this, "敬请期待", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private void openWeb() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://www.aossh.com"));
        startActivity(intent);
    }

    private void showSoundHint() {
        mIsStarted = true;
        soundPool = new SoundPool(10, AudioManager.STREAM_SYSTEM, 5);
        soundPool.load(this, R.raw.bootaudio, 1);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    playSound(sampleId);
                    mHandler.postDelayed(new Runnable() {
                        public void run() {
                            mIsStarted = false;
                            showSpeakDialog();
                        }
                    }, 4000);
                }
            }
        });
    }

    public void playSound(int id) {
        AudioManager am = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        float audioMaxVolumn = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volumnCurrent = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        float volumnRatio = volumnCurrent / audioMaxVolumn;
        soundPool.play(id, volumnRatio, volumnRatio, 1, 1, 1f);
    }

    private void showSpeakDialog() {
        mEdtTransformResult.setText(null);
        setParam();
        boolean isShowDialogII = mSharedPreferences.getBoolean(getString(R.string.pref_key_iat_show), true);
        if (isShowDialogII) {
            mRecognizerDialog.setListener(recognizerDialogListener);
            mWakeUpRecognizer.stop();
            mRecognizerDialog.show();
        } else {
            int errorCode = mSpeechRecognizer.startListening(recognizerListener);
            if (errorCode != ErrorCode.SUCCESS) {
                Toast toastII = Toast.makeText(mContext, "听写失败" + errorCode, Toast.LENGTH_LONG);
                toastII.show();
            }
        }

        mRecognizerDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mWakeUpRecognizer.start();
            }
        });

    }

    /**
     * 准备蓝牙音频
     */
    private void blePrepare() {
        if (!mAudioManager.isBluetoothScoAvailableOffCall()) {
            DebugLog.d(DebugLog.TAG, "MainActivity:startRecording " + "系统不支持蓝牙录音");
            return;
        }

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "该手机没有蓝牙设备", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "蓝牙设备未打开", Toast.LENGTH_SHORT).show();
            return;
        }

        DebugLog.d(DebugLog.TAG, "MainActivity:startRecording " + "系统支持蓝牙录音");
        mAudioManager.stopBluetoothSco();
        mAudioManager.startBluetoothSco();// 蓝牙录音的关键，启动SCO连接，耳机话筒才起作用
        // 注册监听广播
        registerReceiver(mReceiver, new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED));
        isReceivered = true;
    }

    private Receiver mReceiver = new Receiver();

    private class Receiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
            if (AudioManager.SCO_AUDIO_STATE_CONNECTED == state) {
                DebugLog.d(DebugLog.TAG, "MainActivity:onReceive " + "AudioManager.SCO_AUDIO_STATE_CONNECTED");
                mAudioManager.setBluetoothScoOn(true);  // 打开 SCO
                DebugLog.d(DebugLog.TAG, "MainActivity:onReceive " + "Routing:" + mAudioManager.isBluetoothScoOn());
                mAudioManager.setMode(AudioManager.STREAM_MUSIC);
                if (!mIsStarted)
                    showSoundHint();
            } else {// 等待一秒后再尝试启动SCO
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mAudioManager.startBluetoothSco();
                DebugLog.d(DebugLog.TAG, "MainActivity:onReceive " + " 再次 startBluetoothSco() ");
            }
        }
    }

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                Toast toast = Toast.makeText(mContext, "初始化失败", Toast.LENGTH_LONG);
                toast.show();
            }
        }
    };

    private RecognizerListener recognizerListener = new RecognizerListener() {
        @Override
        public void onBeginOfSpeech() {
            //ToDo
        }

        @Override
        public void onEndOfSpeech() {
            //ToDo
        }

        @Override
        public void onError(SpeechError error) {
            DebugLog.i("CollinWang", "Information=" + error.getPlainDescription(true));
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            mEdtTransformResult.append(text);
            mEdtTransformResult.setSelection(mEdtTransformResult.length());
            if (isLast) {
                //ToDo
            }
        }

        @Override
        public void onVolumeChanged(int volume) {
            //ToDo
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
            //ToDo
        }
    };

    private RecognizerDialogListener recognizerDialogListener = new RecognizerDialogListener() {
        public void onResult(RecognizerResult result, boolean isLast) {
            DebugLog.i("CollinWang", "RecognizerResult=" + result.getResultString());
            String text = JsonParser.parseIatResult(result.getResultString());
            mEdtTransformResult.append(text);
            mEdtTransformResult.setSelection(mEdtTransformResult.length());

            if (VoiceCellApplication.mContacts != null) {// 联系人不是空
                if (text.contains("打电话")) {// 是打电话
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber()));
                            MainActivity.this.startActivity(intent);
                            break;
                        }
                    }
                } else if (text.contains("发短信")) {// 发短信
                    for (int i = 0; i < VoiceCellApplication.mContacts.size(); i++) {
                        if (text.contains(VoiceCellApplication.mContacts.get(i).getName())) {
                            Uri smsToUri = Uri.parse("smsto:" + VoiceCellApplication.mContacts.get(i).getPhoneNumber());
                            Intent intent = new Intent(Intent.ACTION_SENDTO, smsToUri);
                            intent.putExtra("sms_body", mSmsBody);
                            MainActivity.this.startActivity(intent);
                            break;
                        }
                    }
                } else {// 拿到短信内容
                    mSmsBody = mEdtTransformResult.getText().toString();
                }
            } else {
                Log.i("CollinWang", "没有联系人");
            }
            // 科大讯飞结束云知声走起
            mWakeUpRecognizer.start();
        }

        public void onError(SpeechError error) {
            DebugLog.i("CollinWang", "Information=" + error.getPlainDescription(true));
        }

    };

    public void setParam() {
        mSpeechRecognizer.setParameter(SpeechConstant.PARAMS, null);
        String lag = mSharedPreferences.getString("iat_language_preference", "mandarin");
        mSpeechRecognizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
        if (lag.equals("en_us")) {
            mSpeechRecognizer.setParameter(SpeechConstant.LANGUAGE, "en_us");
        } else {
            mSpeechRecognizer.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            mSpeechRecognizer.setParameter(SpeechConstant.ACCENT, lag);
        }
        mSpeechRecognizer.setParameter(SpeechConstant.VAD_BOS, mSharedPreferences.getString("iat_vadbos_preference", "4000"));
        mSpeechRecognizer.setParameter(SpeechConstant.VAD_EOS, mSharedPreferences.getString("iat_vadeos_preference", "1000"));
        mSpeechRecognizer.setParameter(SpeechConstant.ASR_PTT, mSharedPreferences.getString("iat_punc_preference", "1"));
        mSpeechRecognizer.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/iflytek/wavaudio.pcm");
    }

    private ContactListener mContactListener = new ContactListener() {
        @Override
        public void onContactQueryFinish(String contactInfos, boolean changeFlag) {
            mSpeechRecognizer.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            mSpeechRecognizer.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
            int ret = mSpeechRecognizer.updateLexicon("contact", contactInfos, lexiconListener);
            if (ret != ErrorCode.SUCCESS) {
                //ToDo
            }
        }
    };

    // 上传联系人监听器
    private LexiconListener lexiconListener = new LexiconListener() {
        @Override
        public void onLexiconUpdated(String lexiconId, SpeechError error) {
            if (error != null) {
                //ToDo
            } else {
                DebugLog.i("CollinWang", "上传联系人成功");
            }
        }
    };

    // 初始化本地离线唤醒
    private void initWakeUp() {
        mWakeUpRecognizer = new WakeUpRecognizer(this, Config.appKey);
        mWakeUpRecognizer.setListener(new WakeUpRecognizerListener() {

            @Override
            public void onWakeUpRecognizerStart() {
                DebugLog.i("CollinWang", "语音唤醒已开始");
            }

            @Override
            public void onWakeUpError(USCError error) {
                if (error != null) {
                    DebugLog.i("CollinWang", "Information=" + error.toString());
                }
            }

            @Override
            public void onWakeUpRecognizerStop() {
                DebugLog.i("CollinWang", "语音唤醒已停止");
            }

            @Override
            public void onWakeUpResult(boolean succeed, String text, float score) {
                if (succeed) {
                    mVibrator.vibrate(300);
                    DebugLog.i("CollinWang", "语音唤醒成功");
                    // 云知声结束停止
                    mWakeUpRecognizer.stop();
                    showSpeakDialog();
                }
            }
        });
    }

    // 启动语音唤醒
    protected void wakeUpStart() {
        mWakeUpRecognizer.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSpeechRecognizer.cancel();
        mSpeechRecognizer.destroy();
        mWakeUpRecognizer.cancel();
        if (isReceivered) {
            unregisterReceiver(mReceiver);
        }
        closeBluetoothScoOn();
    }

    // 关闭蓝牙麦克风语音输入
    private void closeBluetoothScoOn() {
        if (mAudioManager.isBluetoothScoOn()) {
            mAudioManager.setBluetoothScoOn(false);
        }
    }

}
