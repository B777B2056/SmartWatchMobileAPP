package com.example.smartwatchmobileapp;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.InCallService;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class IncomingCallService extends Service {
    private final byte MSG_TYPE_INVALID = 0x00;
    private final byte MSG_TYPE_COMING_CALL_NOTIFY = 0x01;
    private final byte MSG_TYPE_CALL_HANGUP_NOTIFY = 0x02;
    private final byte MSG_TYPE_STEPCNT = 0x03;
    private final byte MSG_TYPE_KAL = 0x04;
    private final byte MSG_TYPE_HEART_RATE = 0x05;
    private final byte MSG_TYPE_SPO2 = 0x06;
    private final byte MSG_TYPE_CALL_REJECT_NOTIFY = 0x07;
    private final byte MSG_TYPE_CALL_ACCEPT_NOTIFY = 0x08;
    private BluetoothSocket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private static final String CHANNEL_ID = "IncomingCallService";
    public static class DataStruct {
        public String stepCnt = null;
        public String kal = null;
        public String heartRate = null;
        public String spo2 = null;
    };
    private final DataStruct mData = new DataStruct();
    public class IncomingCallServiceBinder extends Binder implements OnDataComing {
        @Override
        public DataStruct onDataComingCallback() {
            if ((mData.stepCnt != null) && (mData.kal != null) && (mData.heartRate != null) && (mData.spo2 != null)) {
                return mData;
            } else {
                return null;
            }
        }
    };
    private TelecomManager telecomManager = null;
    public class CallReceiver extends BroadcastReceiver {
        private static final String TAG = "CallReceiver";
        private String lastState = null;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action != null && action.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (state != null) {
                    if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                        String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                        if (phoneNumber == null)    return;
                        Log.d(TAG, "Incoming call from: " + phoneNumber);
                        // 通知手环有来电
                        try {
                            outputStream.write(concatMessage(MSG_TYPE_COMING_CALL_NOTIFY, phoneNumber));
                            telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (state.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                        // 通知手环电话被挂断
                        if (lastState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                            Log.d(TAG, "Incoming call hung up");
                            try {
                                outputStream.write(concatMessage(MSG_TYPE_CALL_HANGUP_NOTIFY, ""));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                        // 通知手环电话被接听
                        if (lastState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                            Log.d(TAG, "Incoming call accept");
                            try {
                                outputStream.write(concatMessage(MSG_TYPE_CALL_ACCEPT_NOTIFY, ""));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    lastState = state;
                }
            }
        }
    }
    public IncomingCallService() {

    }
    @Override
    public IBinder onBind(Intent intent) {
        return new IncomingCallServiceBinder();
    }
    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        this.registerReceiver(new CallReceiver(), new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED));
        try {
            BluetoothDevice device = intent.getExtras().getParcelable("bt_dev");
            socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            socket.connect();
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            new Thread(() -> {
                for (;;) {
                    getMsgFromBluetooth();
                }
            }).start();
        } catch (IOException e) {
            Log.d("IO error", e.getMessage());
            try {
                socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return START_REDELIVER_INTENT;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel Channel = new NotificationChannel(CHANNEL_ID,"主服务",NotificationManager.IMPORTANCE_HIGH);
                Channel.enableLights(true);//设置提示灯
                Channel.setLightColor(Color.RED);//设置提示灯颜色
                Channel.setShowBadge(true);//显示logo
                Channel.setDescription("Smart watch service");//设置描述
                Channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); //设置锁屏可见 VISIBILITY_PUBLIC=可见
                manager.createNotificationChannel(Channel);

                Notification notification = new Notification.Builder(this)
                        .setChannelId(CHANNEL_ID)
                        .setContentTitle("智能手环服务")//标题
                        .setContentText("运行中")//内容
                        .setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher))
                        .build();
                startForeground(1,notification);
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private byte[] concatMessage(byte msg_type, String content) {
        byte[] pkg = new byte[2+content.length()];
        pkg[0] = msg_type;
        pkg[1] = (byte) content.length();
        int i = 2;
        for (byte ch : content.getBytes()) {
            pkg[i++] = ch;
        }
        return pkg;
    }
    private void getMsgFromBluetooth() {
        try {
            byte msgType = (byte) inputStream.read();
            int msgLen = inputStream.read();
            byte[] msg = new byte[msgLen];
            for (int i = 0; i < msgLen; ++i) {
                msg[i] = (byte) inputStream.read();
            }
            switch (msgType) {
                case MSG_TYPE_STEPCNT:
                    mData.stepCnt = new String(msg);
                    break;
                case MSG_TYPE_KAL:
                    mData.kal = new String(msg);
                    break;
                case MSG_TYPE_HEART_RATE:
                    mData.heartRate = new String(msg);
                    break;
                case MSG_TYPE_SPO2:
                    mData.spo2 = new String(msg);
                    break;
                case MSG_TYPE_CALL_REJECT_NOTIFY:
                    // 拒绝来电
                    Log.d("Incoming call: ", "MSG_TYPE_CALL_REJECT_NOTIFY");
                    if (telecomManager != null) {
                        try {
                            Method method = telecomManager.getClass().getMethod("endCall");
                            method.invoke(telecomManager);
                        } catch (NoSuchMethodException | IllegalAccessException |
                                 InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (IOException ignored) {
        }
    }
    public interface OnDataComing {
        DataStruct onDataComingCallback();
    }
}
