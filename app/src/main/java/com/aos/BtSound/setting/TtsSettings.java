package com.aos.BtSound.setting;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.view.Window;

import com.aos.BtSound.util.SettingTextWatcher;

/**
 * 类名：TtsSettings.java
 * 注释：语音合成设置
 * 日期：2015年8月15日
 * 作者：王超
 */
public class TtsSettings extends PreferenceActivity implements OnPreferenceChangeListener {
    public static final String PREFER_NAME = "com.iflytek.setting";
    private EditTextPreference mSpeedPreference;
    private EditTextPreference mPitchPreference;
    private EditTextPreference mVolumePreference;

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        // 指定保存文件名字
        getPreferenceManager().setSharedPreferencesName(PREFER_NAME);
        mSpeedPreference = (EditTextPreference) findPreference("speed_preference");
        mSpeedPreference.getEditText().addTextChangedListener(new SettingTextWatcher(TtsSettings.this, mSpeedPreference, 0, 200));
        mPitchPreference = (EditTextPreference) findPreference("pitch_preference");
        mPitchPreference.getEditText().addTextChangedListener(new SettingTextWatcher(TtsSettings.this, mPitchPreference, 0, 100));
        mVolumePreference = (EditTextPreference) findPreference("volume_preference");
        mVolumePreference.getEditText().addTextChangedListener(new SettingTextWatcher(TtsSettings.this, mVolumePreference, 0, 100));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

}