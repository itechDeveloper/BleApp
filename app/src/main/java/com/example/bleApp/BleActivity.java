package com.example.bleApp;

import static com.example.bleApp.R.drawable.avd_done;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.example.bleApp.client.ClientService;
import com.example.bleApp.databinding.ActivityBleBinding;
import com.example.bleApp.server.ServerService;

public class BleActivity extends AppCompatActivity {

    private static final String TAG = "MAIN ACTIVITY";
    private static final int CLIENT_JOB_SERVICE_ID = 101;
    private static final int SERVER_JOB_SERVICE_ID = 102;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_FINE_LOCATION = 2;

    public static BluetoothManager bluetoothManager;
    public static BluetoothAdapter bluetoothAdapter;

    public static ActivityBleBinding binding;

    public static AnimatedVectorDrawableCompat avd;
    public static AnimatedVectorDrawable avd2;

    public void initManager() {
        bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (!bluetoothAdapter.isEnabled()){
            bluetoothAdapter.enable();
        }
    }

    @Override
    public void onBackPressed() {
        if (bluetoothAdapter.isEnabled()){
            bluetoothAdapter.disable();
        }
        finishAffinity();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_ble);
        binding.imageDone.setImageResource(avd_done);

        Drawable progressDrawable = binding.progressBar.getIndeterminateDrawable().mutate();
        progressDrawable.setColorFilter(Color.BLACK, android.graphics.PorterDuff.Mode.SRC_IN);
        binding.progressBar.setProgressDrawable(progressDrawable);

        initManager();
        new Handler().postDelayed(this::setJobs, 2000);
    }

    public static void setTextView(ActivityBleBinding binding, String text) {
        binding.textInfo.setText(text);
    }

    private void setJobs() {
        if (hasPermissions()) {
            scheduleJob(ClientService.class, "ClientService: ", CLIENT_JOB_SERVICE_ID);
            scheduleJob(ServerService.class, "ServerService: ", SERVER_JOB_SERVICE_ID);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothAdapter.isEnabled()){
            // Check low energy support
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                log("No LE Support.");
                finish();
            }

            // onSERVER: Check advertising
            if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
                // Unable to run the server on this device, get a better device
                log("No Advertising Support.");
                finish();
            }
        }
    }

    public void log(String msg) {
        Log.d(TAG, msg);
    }

    private void scheduleJob(Class serviceClass, String message, int ID) {
        ComponentName componentName = new ComponentName(this, serviceClass);
        JobInfo info = new JobInfo.Builder(ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();

        JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
        int resultCode = scheduler.schedule(info);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            log(message + " Job Scheduled!");
        } else {
            log(message + " Job Scheduling failed!");
        }
    }

    //CLIENT PERMISSIONS

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //If user presses allow"
                    setJobs();
                } else {
                    //If user presses deny
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private boolean hasPermissions() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        log("Permission!");
        return true;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        /*Permission check*/if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {return;}
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        log("Requested user enables Bluetooth. Try starting the scan again.");
    }

    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }

        log("has no location permission.");
        return false;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
        log("Requested user enable Location. Try starting the scan again.");
    }

    public static void confirm() {
        Drawable drawable = binding.imageDone.getDrawable();
        if (drawable instanceof AnimatedVectorDrawableCompat){
            avd = (AnimatedVectorDrawableCompat) drawable;
            avd.start();
        }else if(drawable instanceof AnimatedVectorDrawable){
            avd2 = (AnimatedVectorDrawable) drawable;
            avd2.start();
        }
    }
}