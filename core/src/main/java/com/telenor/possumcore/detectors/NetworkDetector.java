package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonArray;
import com.telenor.possumcore.abstractdetectors.AbstractReceiverDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

import java.util.List;

/**
 * Scans for your nearby networks in order to check whether your time and place
 * matches up with your regular routine. Note that iOS can only retrieve your
 * presently connected network without some private api access
 *
 * Note also - if there is no position allowed on the phone, on some phones wifi will not work
 * In short: With no coarse/fine position permission allowed, wifi will not return scanresults.
 */
public class NetworkDetector extends AbstractReceiverDetector {
    private WifiManager wifiManager;
    private int wifiState = WifiManager.WIFI_STATE_DISABLED;
    private ConnectivityManager connectivityManager;
    private NetworkInfo.State networkState = NetworkInfo.State.DISCONNECTED;

    public NetworkDetector(@NonNull Context context) {
        this(context, null);
    }

    public NetworkDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        addFilterAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        addFilterAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        addFilterAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager)context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null && connectivityManager.getActiveNetworkInfo() != null) {
            networkState = connectivityManager.getActiveNetworkInfo().getState();
            if (ConnectivityManager.TYPE_WIFI == connectivityManager.getActiveNetworkInfo().getType()) {
                wifiState = WifiManager.WIFI_STATE_ENABLED;
            }
        }
    }

    @Override
    public int detectorType() {
        return DetectorType.Network;
    }

    @Override
    public String detectorName() {
        return "network";
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled() && wifiManager != null;
    }

    @Override
    public int queueLimit(@NonNull String key) {
        return 50; // Default set
    }

    @Override
    public boolean isAvailable() {
        return super.isAvailable() && wifiManager != null && wifiManager.isWifiEnabled(); //  && wifiState == WifiManager.WIFI_STATE_ENABLED
    }

    @SuppressWarnings("requiredPermission")
    @Override
    public void run() {
        super.run();
        if (isEnabled() && isAvailable()) {
            if (!wifiManager.startScan()) {
                Log.e(tag, "AP: Failed to start network scan");
            }
            // Immediately get latest scan
            storeResults(wifiManager.getScanResults());
        }
    }

    private int isConnectedToNetwork(String BSSID) {
        // 1 = connected, 0 = not connected
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (wifiInfo == null || networkInfo == null) return 0;
        return networkInfo.isConnected() && wifiInfo.getBSSID().equals(BSSID)?1:0;
    }

    private void storeResults(List<ScanResult> results) {
        for (ScanResult scanResult : results) {
            JsonArray data = new JsonArray();
            data.add(""+now());
            data.add(scanResult.BSSID);
            data.add(""+scanResult.level);
            // TODO: Reimplement isConnectedToNetwork
//            data.add(""+isConnectedToNetwork(scanResult.BSSID));
            streamData(data);
        }

    }

    @Override
    public String requiredPermission() {
        // Network could possibly also require location as documentation states it needs coarse/fine
        // position in order to get wifi scanResults (for some phones)
        return Manifest.permission.CHANGE_WIFI_STATE;
    }

    @Override
    protected void onReceiveData(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        switch (intent.getAction()) {
            case WifiManager.SCAN_RESULTS_AVAILABLE_ACTION:
                storeResults(wifiManager.getScanResults());
                if (isRunning()) {
                    wifiManager.startScan();// Keeps scanning
                }
                break;
            case WifiManager.WIFI_STATE_CHANGED_ACTION:
                int prevState = wifiState;
                wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN);
                if (prevState != wifiState) {
                    detectorStatusChanged();
                    if (wifiState == WifiManager.WIFI_STATE_ENABLED && isRunning())
                        wifiManager.startScan();
                }
                break;
            case WifiManager.NETWORK_STATE_CHANGED_ACTION:
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    networkState = networkInfo.getState();
                }
                break;
            default:
                Log.i(tag, "AP: Unknown action:"+intent.getAction());
        }
    }
}