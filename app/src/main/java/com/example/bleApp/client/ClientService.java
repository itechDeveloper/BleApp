package com.example.bleApp.client;

import static com.example.bleApp.Constants.*;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.example.bleApp.BleActivity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClientService extends JobService {

    //Service variables
    private static final String TAG = "CLIENT SERVICE";
    private boolean jobCancelled = false;

    //Client variables
    private boolean mScanning;
    private Handler mHandler;
    private Map<String, BluetoothDevice> mScanResults;

    private boolean mConnected;
    BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private BluetoothGatt mGatt;

    private boolean mInitialized;

    boolean noScanResult;

    boolean closeEnough;
    int mRSSI;

    boolean canChangeInfo = true;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        log("Job Started");
        mBluetoothAdapter = BleActivity.bluetoothAdapter;
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
            startScan();
            log("Job Finished!");
            jobFinished(jobParameters, false);
        }).start();
    }

    // Logging
    public void log(String msg) {
        Log.d(TAG, msg);
    }

    public void logError(String msg) {
        log("Error: " + msg);
    }

    // Scanning
    private void startScan() {

        if (mScanning){
            return;
        }

        disconnectGattServer();

        mScanResults = new HashMap<>();
        mScanCallback = new BtleScanCallback(mScanResults);

        mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build();

        if (!MATCHED){
            mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
            workerThread();

            mScanning = true;
            log("Started scanning.");
        }
    }

    private void restartScan(){
        mScanning = false;
        noScanResult = true;
        startScan();
    }

    void workerThread() {
        ContextCompat.getMainExecutor(getApplicationContext()).execute(() -> {
            mHandler = new Handler();
            mHandler.postDelayed(this::stopScan, SCAN_PERIOD);
        });
    }

    private void stopScan() {
        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            mBluetoothLeScanner.stopScan(mScanCallback);
            scanComplete();
        }

        if (!noScanResult){
            mScanCallback = null;
            mScanning = false;
            mHandler = null;
        }

        log("Stopped scanning.");
    }

    private void scanComplete() {
        if (!mScanResults.isEmpty()) {
            for (String deviceAddress : mScanResults.keySet()) {
                BluetoothDevice device = mScanResults.get(deviceAddress);
                connectDevice(Objects.requireNonNull(device)); //CONNECTING GATT AUTOMATICALLY
                log("Connecting automatically to " + device);
                noScanResult = false;
            }
        }else{
            log("No scanning result.");
            restartScan();
        }
    }

    // Gatt connection
    private void connectDevice(BluetoothDevice device) {
        log("Connecting to " + device.getAddress());
        GattClientCallback gattClientCallback = new GattClientCallback();
        mGatt = device.connectGatt(this, false, gattClientCallback);
    }

    public void setConnected(boolean connected) {
        mConnected = connected;
    }

    public void disconnectGattServer() {
        log("Closing Gatt connection");
        mConnected = false;
        if (mGatt != null) {
            mGatt.disconnect();
            mGatt.close();
        }
    }

    private class BtleScanCallback extends ScanCallback {

        private final Map<String, BluetoothDevice> mScanResults;

        BtleScanCallback(Map<String, BluetoothDevice> scanResults) {
            mScanResults = scanResults;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            logError("BLE Scan Failed with code " + errorCode);
            restartScan();
        }

        private void addScanResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            String deviceAddress = device.getAddress();
            mScanResults.put(deviceAddress, device);
        }
    }

    private class GattClientCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            log("onConnectionStateChange newState: " + newState);

            if (status == BluetoothGatt.GATT_FAILURE) {
                logError("Connection Gatt failure status " + status);
                disconnectGattServer();
                restartScan();
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                // handle anything not SUCCESS as failure
                logError("Connection not GATT success status " + status);
                disconnectGattServer();
                restartScan();
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected to device: " + gatt.getDevice().getAddress() + " name: " + gatt.getDevice().getName());
                setConnected(true);
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected from device");
                disconnectGattServer();
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (status == BluetoothGatt.GATT_SUCCESS){
                log(String.format("Bluetooth Read RSSI [%d]", rssi));
                mRSSI = rssi;
            }
        }

        //DISCOVER SERVICES & CALL sendMessage()
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logError("No services");
                restartScan();
                return;
            }

            log("Service Discovered!");
            BluetoothGattService service = mGatt.getService(SERVICE_UUID);
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                mInitialized = gatt.setCharacteristicNotification(characteristic, true);
                log("onService: " + service);
                sendMessage(XEE_KIDS_MATCH_CONTROL.getBytes(StandardCharsets.UTF_8));
            } else {
                workerThreadMessage("SERVICE NOT FOUND!");
                logError("onService: null");
                restartScan();
            }
        }

        // CHARACTERISTIC CHANGED
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            readMessage(characteristic);
        }

        // READ DATA FROM SERVER
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            readMessage(characteristic);
        }
    }

    //SEND MESSAGE TO GATT SERVER
    private void sendMessage(byte[] message) {
        if (!MATCHED){
            if (!mConnected || !mInitialized) {
                startScan();
                return;
            }
            BluetoothGattService service = mGatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
            characteristic.setValue(message);
            boolean success = mGatt.writeCharacteristic(characteristic);
            log("onSendMessage: " + success);
        }
    }

    private void readMessage(BluetoothGattCharacteristic characteristic){
        byte[] messageBytes = characteristic.getValue();

        //Reverse received message back
        byte[] reversedMessage = new byte[messageBytes.length];
        for (int i = 0; i < messageBytes.length; i++){
            reversedMessage[i] = messageBytes[messageBytes.length - i - 1];
        }
        String messageString; //received message
        messageString = new String(reversedMessage, StandardCharsets.UTF_8);

        // BECOME FRIENDS HERE!!
        if (messageString.equals(XEE_KIDS_MATCH_CONTROL)){
            setInfo("CLIENT IS READY TO BE FRIENDS!");
            log("Ready to be friends!");
            workerThreadDistance(1);
        }else{
            setInfo("DEVICE IS NOT XEE-KIDS WATCH!");
            log("NOT FRIENDS");
            restartScan();
        }
    }

    //TOAST INFO (TEMP)
    public void workerThreadMessage(String message){
        ContextCompat.getMainExecutor(getApplicationContext()).execute(() ->
                Toast.makeText(getApplicationContext(), message + "", Toast.LENGTH_SHORT).show());
    }

    private void checkDistance(){
        mGatt.readRemoteRssi();
        if (mRSSI >= -60 && mRSSI < 0){
            log("RSSI VALUE WHILE CONNECTING!" + mRSSI);
            closeEnough = true;
        }
    }

    public void workerThreadDistance(long delaySeconds) {
        if (!MATCHED){
            ContextCompat.getMainExecutor(getApplicationContext()).execute(() ->
                    new Handler().postDelayed(() -> {
                        if (closeEnough) {
                            becomeFriend();
                        } else {
                            checkDistance();
                            workerThreadDistance(1);
                        }
                    }, delaySeconds * 1000));
        }
    }

    public void becomeFriend(){
        if (!MATCHED){
            setInfo("CLIENT IS FRIEND!");
            log("FRIENDS!");
            canChangeInfo = false;
            sendMessage(XEE_KIDS_FRIEND.getBytes(StandardCharsets.UTF_8));
            BleActivity.confirm();
            Drawable progressDrawable = BleActivity.binding.progressBar.getIndeterminateDrawable().mutate();
            progressDrawable.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
            BleActivity.binding.progressBar.setProgressDrawable(progressDrawable);

            mBluetoothAdapter.disable();
        }
    }

    private void setInfo(String message){
        if (canChangeInfo){
            BleActivity.setTextView(BleActivity.binding,message);
        }
    }
}
