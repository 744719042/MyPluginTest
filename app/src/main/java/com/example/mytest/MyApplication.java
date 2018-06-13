package com.example.mytest;

import android.app.Application;

public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        PluginManager.getInstance().init(this);
        HookStartActivity.hook(this);
    }
}
