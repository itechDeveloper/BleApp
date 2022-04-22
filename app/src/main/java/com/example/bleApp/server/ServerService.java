package com.example.bleApp.server;

import static com.example.bleApp.Constants.*;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.bleApp.BleActivity;
import com.example.bleApp.util.ByteUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ServerService extends JobService {

    //Service variables
    private static final String TAG = "SERVER SERVICE";
    private boolean jobCancelled = false;

    //Server variables
    private Handler mHandler;
    private List<BluetoothDevice> mDevices;

    private BluetoothGattServer mGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    boolean canChangeInfo = true;

    BluetoothAdapter mBluetoothAdapter;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        log("Job Started");
        doBackgroundWork(jobParameters);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        logError("Job cancelled before completion.");
        jobCancelled = true;
        return true;
    }

    private void doBackgroundWork(JobParameters jobParameters) {
        new Thread(() -> {
            if (jobCancelled) {
                return;
            }
            initializing();
            setupServer();
            startAdvertising();
            log("Job Finished!");
            jobFinished(jobParameters, false);
        }).start();
    }

    // Logging
    public void log(String msg) {
        Log.e(TAG, msg);
    }

    public void logError(String msg) {
        log("Error: " + msg);
    }

    private void initializing() {
        mDevices = new ArrayList<>();
        BluetoothManager mBluetoothManager = BleActivity.bluetoothManager;
        mBluetoothAdapter = BleActivity.bluetoothAdapter;

        mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        GattServerCallback gattServerCallback = new GattServerCallback();

        mGattServer = mBluetoothManager.openGattServer(this, gattServerCallback);
    }

    // Gatt Server

    private void setupServer() {
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic writeCharacteristic = new BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);
        service.addCharacteristic(writeCharacteristic);

        mGattServer.addService(service);
    }

    private void stopServer() {
        if (mGattServer != null) {
            mGattServer.close();
        }
    }

    // Advertising

    private void startAdvertising() {
        if (mBluetoothLeAdvertiser == null) {
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .build();

        ParcelUuid parcelUuid = new ParcelUuid(SERVICE_UUID);
        AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false)
                .addServiceUuid(parcelUuid)
                .build();

        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser != null) {
            mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            log("Peripheral advertising started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            log("Peripheral advertising failed: " + errorCode);
        }
    };

    // Gatt Server Actions

    public void addDevice(BluetoothDevice device) {
        log("Device added: " + device.getAddress());
        workerAddDeviceThread(device);
    }

    public void removeDevice(BluetoothDevice device) {
        log("Device removed: " + device.getAddress());
        workerRemoveDeviceThread(device);
    }

    void workerAddDeviceThread(BluetoothDevice device) {
        ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
            mHandler = new Handler();
            mHandler.post(() -> mDevices.add(device));

        });
    }

    void workerRemoveDeviceThread(BluetoothDevice device) {
        ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
            mHandler = new Handler();
            mHandler.post(() -> mDevices.remove(device));
        });
    }

    // Gatt CallBack

    private class GattServerCallback extends BluetoothGattServerCallback {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            log("onConnectionStateChange " + device.getAddress() + "\nstatus " + status + "\nnewState " + newState);

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                addDevice(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                removeDevice(device);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device,
                    requestId,
                    characteristic,
                    preparedWrite,
                    responseNeeded,
                    offset,
                    value);

            String val = new String(value, StandardCharsets.UTF_8);
            if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                if (val.equals(XEE_KIDS_MATCH_CONTROL)) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);

                    //NOTIFY ALL CONNECTED DEVICES FOR CHARACTERISTIC
                    characteristic.setValue(ByteUtils.reverse(value));
                    for (BluetoothDevice devices : mDevices) {
                        mGattServer.notifyCharacteristicChanged(devices, characteristic, false);
                    }

                    setInfo("SERVER IS READY TO BE FRIENDS!");
                    log("Server is ready to become friend!");
                }else if (val.equals(XEE_KIDS_FRIEND)){
                    setInfo("SERVER IS FRIEND!");
                    log("FRIENDS!");
                    canChangeInfo = false;
                    MATCHED = true;

                    //ui changes
                    Drawable progressDrawable = BleActivity.binding.progressBar.getIndeterminateDrawable().mutate();
                    progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                    BleActivity.binding.progressBar.setProgressDrawable(progressDrawable);
                    BleActivity.confirm();

                    stopAdvertising();
                    stopServer();
                    mBluetoothAdapter.disable();
                }else{
                    setInfo("Device sent message is not Xee Kids Watch!");
                    log("Device sent message is not Xee Kids Watch!");
                }
            }else{
                setInfo("UUDIs don't match!");
                log("UUDIs don't match!");
            }
        }
    }

    //TOAST INFO (TEMP)
    public void workerThreadMessage(String message){
        ContextCompat.getMainExecutor(getApplicationContext()).execute(() ->
                Toast.makeText(getApplicationContext(), message + "", Toast.LENGTH_SHORT).show());
    }

    private void setInfo(String message){
        if (canChangeInfo){
            BleActivity.setTextView(BleActivity.binding,message);
        }
    }
}
