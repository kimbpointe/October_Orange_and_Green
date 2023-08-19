package com.example.hugsapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.example.hugsapp.models.DeviceAdapter;
import com.example.hugsapp.models.RemoteConfiguration;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class BeginSession extends AppCompatActivity implements DeviceAdapter.OnDeviceItemListener {

    int dataChoice = -1; //force = 0, camera = 1
    ImageView video, force, back;
    Button start;
    Dialog connectDialog;
    int duration;
    RemoteConfiguration config;
    LottieAnimationView loading;
    TextView loadingText;
    DeviceAdapter deviceAdapter;
    ArrayList<BluetoothDevice> devices;
    RecyclerView deviceList;

    //Bluetooth variables
    BluetoothAdapter mBluetoothAdapter;
    static BluetoothLeService mBluetoothLeService;
    private final ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    static BluetoothGattCharacteristic mNotifyCharacteristic;
    static BluetoothDevice mainDevice;
    static String mDeviceAddress;
    private String mDeviceName;


    // Obtain the FirebaseAnalytics instance.

    FirebaseAnalytics mFirebaseAnalytics;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e("BLUETOOTH_STATUS", "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
//                updateConnectionState("Connected");
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
//                updateConnectionState("Disconnected");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {

                // Show all the supported services and characteristics.
                for (BluetoothGattService gattService : mBluetoothLeService.getSupportedGattServices()) {
                    Log.i("BT_SERVICES_CHARS", "Service: "+gattService.toString());
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        String uuid = gattCharacteristic.getUuid().toString();
                        Log.i("BT_SERVICES_CHARS", "UUID characteristic: "+ uuid);
                        if (uuid.equals("19b10001-e8f2-537e-4f6c-d104768a1214")) {
                            mBluetoothLeService.readCharacteristic(gattCharacteristic);
                            mNotifyCharacteristic = gattCharacteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    mNotifyCharacteristic, true);
                        }
                    }
                }

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] data = intent.getExtras().getByteArray(BluetoothLeService.EXTRA_DATA);
                int size = (int) Math.ceil(data.length/2);
                int[] channel = new int[size];
                int j = 0;
                for (int i = 0; i < size; i+=2) {
                    channel[j] = (data[i]<<8) + (data[i+1] & 0xFF);
                    j++;
                }
                mBluetoothLeService.readCharacteristic(mNotifyCharacteristic); //trigger another read characteristic
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_beign_session);
        config = new RemoteConfiguration(this);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        Bundle extras = getIntent().getExtras();
        if(extras == null) {
            duration = config.getSessionDuration();
        } else {
            duration = extras.getInt("duration");
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (MainActivity.mBluetoothLeService != null) {
            final boolean result = MainActivity.mBluetoothLeService.connect(MainActivity.mDeviceAddress);
            Log.d("BT_STATUS", "Connect request result=" + result);
        }
        try {
            MainActivity.mBluetoothLeService.readCharacteristic(MainActivity.mNotifyCharacteristic);
        } catch (Exception e) {}

        video = findViewById(R.id.camera);
        force = findViewById(R.id.hand);
        start = findViewById(R.id.startSession);
        back = findViewById(R.id.back2);
        loading = findViewById(R.id.loadingAnimate);
        loadingText = findViewById(R.id.loadingText);

        connectDialog = new Dialog(this);

        start.setVisibility(View.VISIBLE);
        loading.setVisibility(View.INVISIBLE);
        loadingText.setVisibility(View.INVISIBLE);

        requestMultiplePermission(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.WRITE_EXTERNAL_STORAGE}, "Permissions Needed", "Please enable all permissions in settings to use this app", 123);


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        devices = new ArrayList<>();

        video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataChoice = 1;
                setData(dataChoice);
            }
        });

        force.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dataChoice = 0;
                setData(dataChoice);
            }
        });

        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getApplicationContext(), MainActivity.class));
                finish();
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dataChoice == 1) {
                    Intent video = new Intent(getApplicationContext(), VideoService.class);
                    startActivity(video);
                } else if (dataChoice == -1) {
                    Toast.makeText(getApplicationContext(), "Select Data", Toast.LENGTH_SHORT).show();
                }
                else {
                    // next screen
                    openConnectDialog();
                }
            }
        });


    }

    public void openConnectDialog() {
        connectDialog.setContentView(R.layout.connect_dialog);
        connectDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView title = connectDialog.findViewById(R.id.title);
        TextView ok = connectDialog.findViewById(R.id.done_text);
        deviceList = connectDialog.findViewById(R.id.deviceList);
        ImageView close = connectDialog.findViewById(R.id.close);


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {}
        mBluetoothAdapter.startDiscovery();

        deviceAdapter = new DeviceAdapter(devices, getApplicationContext(), this);

        deviceList.setAdapter(deviceAdapter);
        deviceList.setLayoutManager(new LinearLayoutManager(getApplicationContext()));


        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectDialog.dismiss();
                Intent start = new Intent(getApplicationContext(), Session.class);
                start.putExtra("data type", dataChoice);
                start.putExtra("duration", duration);

                startActivity(start);
                finish();
            }
        });


        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectDialog.dismiss();
            }
        });

        connectDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                //Go to session start
//                startActivity(new Intent(getApplicationContext(), BeginSession.class));
            }
        });

        connectDialog.show();
    }

    @Override
    public void OnDeviceItemClick(View v, int position) {
        mainDevice = devices.get(position);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        }
        if (mainDevice.getName().contains("Arduino")) {

            mDeviceName = mainDevice.getName();
            mDeviceAddress = mainDevice.getAddress();

            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d("BT_STATUS", "Connect request result=" + result);


            connectDialog.dismiss();

            //Go to session start
            startActivity(new Intent(this, Session.class));

        } else {
            Toast.makeText(getApplicationContext(), "Invalid Device", Toast.LENGTH_SHORT).show();
        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(BeginSession.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {}
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
                try {
//                    Log.i("BT_SCAN", deviceName);
//                    Log.i("BT_SCAN", deviceHardwareAddress);

                    if(!deviceExists(devices, deviceHardwareAddress)) {
                        if(deviceName.contains("Arduino")) {
                            devices.add(0, device);
                        } else {
                            devices.add(device);
                        }
                        deviceAdapter.notifyDataSetChanged();
                    }
                } catch (Exception e) {}
            }
        }
    };


    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, filter);

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d("BT_STATUS", "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }


    private void displayData(String data) {
        if (data != null) {
            Log.i("BT_DATA", data);
//            mDataField.setText(data);
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public boolean deviceExists(ArrayList<BluetoothDevice> list, String mac) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getAddress().equals(mac)) {
                return true;
            }
        }
        return false;
    }

    public void openFolder() {
        final File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + "/HUGS/");
        // location = "/sdcard/my_folder";
        Intent intent = new Intent(Intent.ACTION_VIEW);
//        Uri mydir = Uri.parse("file://"+location);
        Uri mydir = Uri.parse(path.toURI().toString());
        intent.setDataAndType(mydir,"application/*");    // or use */*
        startActivity(intent);
    }

    private void requestMultiplePermission(String[] permissions, String title, String reason, int RESULT_CODE) {

        boolean showPersonalDialogRequest = false;
        for (int i = 0; i < permissions.length; i++) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                showPersonalDialogRequest = true;
                break;
            }
        }
        if (showPersonalDialogRequest) {
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(reason)
                    .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create().show();
        } else {
            ActivityCompat.requestPermissions(this,
                    permissions, RESULT_CODE);
        }
    }

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.




    public void setData(int dataSelection) {
        if (dataSelection == 1) {
            video.setBackground(getApplicationContext().getDrawable(R.drawable.select_circle));
            force.setBackground(getApplicationContext().getDrawable(R.drawable.clear));
        } else if (dataSelection == 0) {
            video.setBackground(getApplicationContext().getDrawable(R.drawable.clear));
            force.setBackground(getApplicationContext().getDrawable(R.drawable.select_circle));
        }
    }
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        unbindService(MainActivity.mServiceConnection);
//        MainActivity.mBluetoothLeService = null;
//
//        // Don't forget to unregister the ACTION_FOUND receiver.
//        unregisterReceiver(mReceiver);
//    }
}