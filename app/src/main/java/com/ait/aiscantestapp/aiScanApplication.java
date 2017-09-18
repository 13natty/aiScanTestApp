package com.ait.aiscantestapp;

import android.app.Activity;

import com.ait.aiscan.manager.api.EyeTPublic;


/**
 * Created by 13nat on 08-Sep-17.
 */

public class aiScanApplication extends android.app.Application {
    public final static String CRASHLYTICS_KEY_CRASHES = "are_crashes_enabled";
    private EyeTPublic mEyetPublic;

    @Override
    public void onCreate() {
        super.onCreate();


        //  if (LeakCanary.isInAnalyzerProcess(this)) {
        //       // This process is dedicated to LeakCanary for heap analysis.
        // You should not init your app in this process.
        //        return;
        //     }
        //     LeakCanary.install(this);
        // Normal app init code...
    }

    public EyeTPublic getmEyetPublic(Activity activity) {
        if (null == mEyetPublic)
        {
            return new EyeTPublic.EyeTFactory().createEyeT(this);
            //return new EyeTPublicImpl(this);
        }
        else return mEyetPublic;
    }
}