package com.example.smartwatchmobileapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.role.RoleManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private ArrayAdapter<String> pairedDeviceListAdapter, newDeviceListAdapter;
    private final ArrayList<BluetoothDevice> pairedDevices = new ArrayList<>();
    private final ArrayList<BluetoothDevice> newDevices = new ArrayList<>();
    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    @SuppressLint("MissingPermission")
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                // 搜索开始
                Toast.makeText(MainActivity.this, "Scan start", Toast.LENGTH_SHORT).show();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // 发现设备
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 在此处理发现的蓝牙设备
                if (!newDevices.contains(device)) {
                    newDevices.add(device);
                    newDeviceListAdapter.add(device.getName() + "\n" + device.getAddress());
                    newDeviceListAdapter.notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // 搜索结束
                Toast.makeText(MainActivity.this, "Scan end", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final ActivityResultLauncher<Intent> mBluetoothLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                addPairedBluetoothDevs();
                discoveryNewBluetoothDevs();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.dynamicAcquirePermission();
        if (bluetoothAdapter == null) {
            Toast.makeText(MainActivity.this, "Device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        this.listViewInit();
        this.openBluetooth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(bluetoothReceiver, makeFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(bluetoothReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void dynamicAcquirePermission() {
        // 来电权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
            startActivityForResult(intent, 1);
        }
        //判断是否有访问位置的权限，没有权限，直接申请位置权限
        if ((checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                || (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 200);
        }
        // 请求电话状态权限
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    1);
        }
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALL_LOG},
                    1);
        }
        if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ANSWER_PHONE_CALLS},
                    1);
        }
    }
    private void listViewInit() {
        ListView newDeviceListView = findViewById(R.id.bt_list2);
        newDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        newDeviceListView.setAdapter(newDeviceListAdapter);
        newDeviceListView.setOnItemClickListener((parent, view, position, id) -> connectToDevice(newDevices.get(position)));

        ListView pairedDeviceListView = findViewById(R.id.bt_list);
        pairedDeviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        pairedDeviceListView.setAdapter(pairedDeviceListAdapter);
        pairedDeviceListView.setOnItemClickListener((parent, view, position, id) -> connectToDevice(pairedDevices.get(position)));
    }

    @SuppressLint("MissingPermission")
    private void openBluetooth() {
        if (!bluetoothAdapter.isEnabled()) {
            mBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            this.addPairedBluetoothDevs();
            this.discoveryNewBluetoothDevs();
        }
    }

    @SuppressLint("MissingPermission")
    private void addPairedBluetoothDevs() {
        // 添加已配对设备到列表
        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            pairedDevices.add(device);
            pairedDeviceListAdapter.add(device.getName() + "\n" + device.getAddress());
            pairedDeviceListAdapter.notifyDataSetChanged();
        }
    }

    @SuppressLint("MissingPermission")
    private void discoveryNewBluetoothDevs() {
        newDeviceListAdapter.clear();
        newDevices.clear();
        // 添加新设备
        registerReceiver(bluetoothReceiver, makeFilter());
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        if (!bluetoothAdapter.startDiscovery()) {
            Toast.makeText(MainActivity.this, "Failed to scan bluetooth devices", Toast.LENGTH_SHORT).show();
        }
    }
    private static IntentFilter makeFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙状态改变的广播
        filter.addAction(BluetoothDevice.ACTION_FOUND);//找到设备的广播
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);//搜索完成的广播
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);//开始扫描的广播
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//状态改变
        return filter;
    }
    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        bluetoothAdapter.cancelDiscovery();
        Intent intent = new Intent(MainActivity.this, IncomingCallService.class);
        intent.setAction("android.intent.action.RESPOND_VIA_MESSAGE");
        intent.setAction("com.example.smartwatchmobileapp.IncomingCallService.action.INIT");
        intent.putExtra("bt_dev", device);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        startActivity(new Intent(MainActivity.this, PlotActivity.class));
    }
}