package com.aos.BtSound;

import android.app.Application;
import android.content.Context;

import com.aos.BtSound.model.ContactInfo;
import com.aos.BtSound.preference.Settings;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechUtility;
import com.umeng.analytics.MobclickAgent;

import java.util.ArrayList;
import java.util.List;

/**
 *类名：VoiceCellApplication.java
 *注释：程序入口
 *日期：2015年8月5日
 *作者：王超
 */
public class VoiceCellApplication extends Application {
	public static Context mApplication = null;
	// 装载联系人信息
	public static List<ContactInfo> mContacts = new ArrayList<ContactInfo>();
	// 短信播报开关
	public static boolean isReadSMS = true;

	@Override
	public void onCreate() {
		super.onCreate();
		mApplication = this;
		// 初始化科大讯飞模块
		StringBuffer param = new StringBuffer();
		param.append("appid=4fd96987");
		param.append(",");
		param.append(SpeechConstant.ENGINE_MODE+"="+SpeechConstant.MODE_MSC);
		SpeechUtility.createUtility(this, param.toString());
		// 数据存储初始化
		Settings.initPreferences(mApplication);
		// 禁止默认统计
		MobclickAgent.openActivityDurationTrack(false);
		// 友盟错误上传
		MobclickAgent.setCatchUncaughtExceptions(true);
	}
}
