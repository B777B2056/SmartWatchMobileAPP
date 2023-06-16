package com.example.smartwatchmobileapp;

import android.app.Application;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.CoordType;
import com.baidu.mapapi.SDKInitializer;

public class SmartWatchMobileApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
//        SDKInitializer.setAgreePrivacy(this, true);
        SDKInitializer.initialize(this);
        LocationClient.setAgreePrivacy(true);
        SDKInitializer.setCoordType(CoordType.BD09LL);
    }
}
