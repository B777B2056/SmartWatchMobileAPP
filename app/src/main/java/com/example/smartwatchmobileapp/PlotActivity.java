package com.example.smartwatchmobileapp;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.track.TraceAnimationListener;
import com.baidu.mapapi.map.track.TraceOptions;
import com.baidu.mapapi.map.track.TraceOverlay;
import com.baidu.mapapi.model.LatLng;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PlotActivity extends AppCompatActivity implements ServiceConnection {
    private MapView mMapView = null;
    private BaiduMap mBaiduMap;
    private LocationClient mLocationClient;
    private DirectionListener mDirectionListener;
    private float mXDirection = 0;
    private boolean isStartSport = false;
    private TraceOverlay mTraceOverlay;
    private final ArrayList<LatLng> mLatLngList = new ArrayList<>();
    private Timer mTimer;
    private final BitmapDescriptor startBD = BitmapDescriptorFactory.fromResource(R.drawable.ic_me_history_startpoint);
    private final BitmapDescriptor finishBD = BitmapDescriptorFactory.fromResource(R.drawable.ic_me_history_finishpoint);
    private class TextUpdateTask extends TimerTask {
        @Override
        public void run() {
            IncomingCallService.DataStruct data = mServiceBinder.onDataComingCallback();
            if (data == null)   return;
            TextView stepcntV = findViewById(R.id.textViewStep);
            stepcntV.setText(String.format("步数：%s", data.stepCnt));
            TextView kalV = findViewById(R.id.textViewKal);
            kalV.setText(String.format("能量：%s", data.kal));
            TextView hrV = findViewById(R.id.textViewHr);
            hrV.setText(String.format("心率：%s", data.heartRate));
            TextView spo2V = findViewById(R.id.textViewSpo2);
            spo2V.setText(String.format("血氧：%s", data.spo2));
        }
    }
    private IncomingCallService.IncomingCallServiceBinder mServiceBinder = null;
    ActivityResultLauncher<Intent> permissionActivityResultLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {});
    public class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            if (location == null || mMapView == null){
                return;
            }
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
            locateAndZoom(location, ll);
            if (isStartSport) {
                mLatLngList.add(ll);
                if (mLatLngList.size() > 2) {
                    initTrace();
                } else if (mLatLngList.size() == 1) {
                    mBaiduMap.addOverlay(new MarkerOptions().position(mLatLngList.get(0)).icon(startBD).zIndex(1));
                }
            }
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plot);
        mTimer = new Timer();
        Intent mServiceIntent = new Intent(this, IncomingCallService.class);
        bindService(mServiceIntent, this, Context.BIND_AUTO_CREATE);
        this.requestPermission();
        this.initBaiduMap();
        this.initViewComponents();
        this.initDirectSensor();
        this.startLocate();
    }
    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();
        mDirectionListener.stop();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
    }
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        mServiceBinder = (IncomingCallService.IncomingCallServiceBinder) iBinder;
        if (mServiceBinder != null) {
            mTimer.schedule(new TextUpdateTask(), 0, 1000);
        }
    }
    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        mServiceBinder = null;
    }
    private void initDirectSensor() {
        mDirectionListener = new DirectionListener(PlotActivity.this);
        mDirectionListener.setOnOrientationListener(x -> mXDirection = x);
        mDirectionListener.start();
    }
    private void initBaiduMap() {
        mMapView = findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setMyLocationEnabled(true);
        MyLocationConfiguration myLocationConfiguration = new MyLocationConfiguration(MyLocationConfiguration.LocationMode.FOLLOWING, true, null);
        mBaiduMap.setMyLocationConfiguration(myLocationConfiguration);
        this.removeTrace();
    }
    private void initViewComponents() {
        Button sportBtn = findViewById(R.id.button);
        sportBtn.setOnClickListener(v -> {
            if (isStartSport) {
                sportBtn.setText("开始运动");
                mBaiduMap.addOverlay(new MarkerOptions().position(mLatLngList.get(mLatLngList.size()-1)).icon(finishBD).zIndex(1));
                mLatLngList.clear();
            } else {
                sportBtn.setText("停止运动");
            }
            isStartSport = !isStartSport;
        });
    }
    private void initTrace() {
        TraceOptions traceOptions = new TraceOptions();
        traceOptions.animationTime(5000);
        traceOptions.animate(true);
        traceOptions.animationType(TraceOptions.TraceAnimateType.TraceOverlayAnimationEasingCurveLinear);
        traceOptions.color(0xAAFF0000);
        traceOptions.width(10);
        traceOptions.points(mLatLngList);
        mTraceOverlay = mBaiduMap.addTraceOverlay(traceOptions, new TraceAnimationListener() {
            @Override
            public void onTraceAnimationUpdate(int percent) {
                // 轨迹动画更新进度回调
            }

            @Override
            public void onTraceUpdatePosition(LatLng position) {
                // 轨迹动画更新的当前位置点回调
            }

            @Override
            public void onTraceAnimationFinish() {
                // 轨迹动画结束回调
            }
        });
    }
    private void startLocate() {
        //定位初始化
        LocationClient.setAgreePrivacy(true);
        try {
            mLocationClient = new LocationClient(getApplicationContext());
        } catch (Exception e) {
            Toast.makeText(PlotActivity.this, "Locate service failed", Toast.LENGTH_SHORT).show();
            return;
        }

        //通过LocationClientOption设置LocationClient相关参数
        LocationClientOption option = new LocationClientOption();
        option.setOpenGps(true); // 打开gps
        option.setCoorType("bd09ll"); // 设置坐标类型
        option.setScanSpan(1000);

        //设置locationClientOption
        mLocationClient.setLocOption(option);

        //注册LocationListener监听器
        MyLocationListener myLocationListener = new MyLocationListener();
        mLocationClient.registerLocationListener(myLocationListener);
        //开启地图定位图层
        mLocationClient.start();
    }
    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PERMISSION_GRANTED) {//判断是否已经赋予权限
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {//30
            // 先判断有没有权限
            if (!Environment.isExternalStorageManager()) {
                //跳转到设置界面引导用户打开
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                permissionActivityResultLauncher.launch(intent);
            }
        }
    }
    private void locateAndZoom(BDLocation location, LatLng ll) {
        MyLocationData locData = new MyLocationData.Builder()
                .accuracy(location.getRadius())
                .direction(mXDirection)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude()).build();
        mBaiduMap.setMyLocationData(locData);
        MapStatus.Builder builder = new MapStatus.Builder();
        builder.target(ll).zoom(18.0f);
        mBaiduMap.animateMapStatus(MapStatusUpdateFactory.newMapStatus(builder.build()));
    }
    private void removeTrace() {
        if (null != mTraceOverlay) {
            mTraceOverlay.clear(); // 清除轨迹数据，但不会移除轨迹覆盖物
            mTraceOverlay.remove(); // 移除轨迹覆盖物
        }
        mBaiduMap.clear();
    }
}