package com.telenor.possumcore.detectors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

import com.google.gson.JsonArray;
import com.telenor.possumcore.abstractdetectors.AbstractReceiverDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

/**
 * Uses bonded bluetooth devices to see if you are close to your environment as well as
 * scanning for your nearby bluetooth devices to determine proximity to known environment
 */
public class BluetoothDetector extends AbstractReceiverDetector {
    private BluetoothAdapter bluetoothAdapter;
//    private Handler handler = new Handler();
    private static final String scanDataSet = "bluetooth_scan";
//    private static final long maxScanTime = 12000; // 12 seconds is maximum scan time
    private static final int[] allStates = {BluetoothProfile.STATE_CONNECTED,
                                            BluetoothProfile.STATE_DISCONNECTED,
                                            BluetoothProfile.STATE_CONNECTING,
                                            BluetoothProfile.STATE_DISCONNECTING};
    private ScanCallback bleScanCallback;

    public BluetoothDetector(@NonNull Context context) {
        this(context, null);
    }
    public BluetoothDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        addFilterAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        addFilterAction(BluetoothDevice.ACTION_FOUND);
        receiverIsAlwaysOn();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) return;
        createDataSet(scanDataSet); // Creates an additional dataSet, one for scanning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            createBleCallback();
        }
    }

    @Override
    public int queueLimit(@NonNull String key) {
        if (key.equals(scanDataSet)) return 20;
        return 20; // Default set
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void createBleCallback() {
        bleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                ScanRecord record = result.getScanRecord();
                int txPowerLvl = Integer.MIN_VALUE;
                if (record != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    txPowerLvl = (short) record.getTxPowerLevel(); // Transmission power level in Db
                }
                BluetoothDevice device = result.getDevice();
                JsonArray data = new JsonArray();
                data.add("" + now()); // Timestamp
                data.add("" + device.getType()); // BLE/classic/both
                data.add(""+device.getBluetoothClass().getDeviceClass()); // Device class
                data.add(device.getAddress()); // mac
                data.add("" + result.getRssi()); // signal strength
                data.add("" + txPowerLvl); // transmission power lvl
                data.add("" + device.getBondState()); // bond state
                streamData(data, scanDataSet);
            }
        };
    }

    private final BluetoothProfile.ServiceListener serviceListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            for (BluetoothDevice device : proxy.getDevicesMatchingConnectionStates(allStates)) {
                JsonArray data = new JsonArray();
                data.add("" + now()); // Timestamp
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    data.add("" + device.getType()); // BLE/classic/both
                } else {
                    data.add("0"); // Corresponds to BluetoothDevice.DEVICE_TYPE_UNKNOWN
                }
                data.add(""+device.getBluetoothClass().getDeviceClass()); // Device class
                data.add(device.getAddress()); // mac
                data.add("" + proxy.getConnectionState(device)); // connection state
                data.add("" + profile); // profile
                streamData(data);
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
        }
    };

    @Override
    protected void onReceiveData(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        detectorStatusChanged();
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && bluetoothAdapter != null;
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    @Override
    public void run() {
        super.run();
        if (isEnabled() && isAvailable()) {
            bluetoothAdapter.getProfileProxy(context(), serviceListener, BluetoothProfile.HEADSET);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                bluetoothAdapter.getProfileProxy(context(), serviceListener, BluetoothProfile.GATT);
                bluetoothAdapter.getProfileProxy(context(), serviceListener, BluetoothProfile.GATT_SERVER);
            }
            bluetoothAdapter.getProfileProxy(context(), serviceListener, BluetoothProfile.A2DP);
            bluetoothAdapter.getProfileProxy(context(), serviceListener, BluetoothProfile.HEALTH);
            if (isBLEDevice()) {
                // Start BLE scan
                scanBLE();
            } else {
                // Start regular scan
                scanRegular();
            }
        }
    }

    /**
     * Method for scanning with regular bluetooth
     */
    private void scanRegular() {
        if (!bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.startDiscovery();
  //          handler.postDelayed(this::terminate, maxScanTime);
        }
    }

    /**
     * Method for stopping a regular bluetooth scan. Note: Since discovery is a heavy process, it
     * should always be stopped
     */
    private void stopRegularScan() {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    /**
     * Method for scanning with Bluetooth Low Energy, requires lollipop
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void scanBLE() {
        ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
        scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);
        bluetoothAdapter.getBluetoothLeScanner().startScan(null, scanSettingsBuilder.build(), bleScanCallback);
    //    handler.postDelayed(this::terminate, maxScanTime);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void stopBLEScan() {
        bluetoothAdapter.getBluetoothLeScanner().stopScan(bleScanCallback);
    }

    @Override
    public void terminate() {
        super.terminate();
        if (isBLEDevice()) {
            stopBLEScan();
        } else {
            stopRegularScan();
        }
    }

    /**
     * Checks whether androids bluetooth is Low Energy or not
     *
     * @return true if it is, false if not
     */
    private boolean isBLEDevice() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && bluetoothAdapter != null && bluetoothAdapter.getBluetoothLeScanner() != null;
    }

    @Override
    public int detectorType() {
        return DetectorType.Bluetooth;
    }

    @Override
    public String detectorName() {
        return "bluetooth";
    }
}