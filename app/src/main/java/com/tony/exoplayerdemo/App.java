package com.tony.exoplayerdemo;

import android.app.Application;
import android.util.Log;

import com.google.android.exoplayer2.util.Util;

/**
 * Created by tony on 2017/10/12.
 */

public class App extends Application {
    protected String userAgent;

    @Override
    public void onCreate() {
        super.onCreate();
        userAgent = Util.getUserAgent(this, getString(R.string.app_name));
        Log.d("exoplayer2", "userAgent=" + userAgent);
    }

    public boolean useExtensionRenderers() {
        return BuildConfig.FLAVOR.equals("withExtensions");
    }
}
