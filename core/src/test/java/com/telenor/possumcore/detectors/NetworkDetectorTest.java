package com.telenor.possumcore.detectors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import com.google.gson.JsonArray;
import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.abstractdetectors.AbstractReceiverDetector;
import com.telenor.possumcore.constants.DetectorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowConnectivityManager;
import org.robolectric.shadows.ShadowWifiManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class NetworkDetectorTest {
    private NetworkDetector networkDetector;
    private ShadowWifiManager shadowWifiManager;
    @Before
    public void setUp() throws Exception {
        networkDetector = new NetworkDetector(RuntimeEnvironment.application);
        Field wifiManagerField = NetworkDetector.class.getDeclaredField("wifiManager");
        wifiManagerField.setAccessible(true);
        WifiManager wifiManager = (WifiManager)wifiManagerField.get(networkDetector);
        Assert.assertNotNull(wifiManager);
        shadowWifiManager = Shadows.shadowOf(wifiManager);
    }

    @After
    public void tearDown() {
        networkDetector = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(networkDetector);
        Assert.assertEquals(DetectorType.Network, networkDetector.detectorType());
        Assert.assertEquals("network", networkDetector.detectorName());
    }

    @Test
    public void testIntentFilterIsCorrect() throws Exception {
        Field intentFilterField = AbstractReceiverDetector.class.getDeclaredField("intentFilter");
        intentFilterField.setAccessible(true);
        IntentFilter storedFilter = (IntentFilter)intentFilterField.get(networkDetector);
        Assert.assertEquals(1, storedFilter.countActions());
        Assert.assertEquals(WifiManager.WIFI_STATE_CHANGED_ACTION, storedFilter.getAction(0));
    }

    @Test
    public void testConnectivityManagerOnInitialize() throws Exception {
        ConnectivityManager connectivityManager = (ConnectivityManager)RuntimeEnvironment.application.getSystemService(Context.CONNECTIVITY_SERVICE);
        Assert.assertNotNull(connectivityManager);
        ShadowConnectivityManager shadowConnectivityManager = Shadows.shadowOf(connectivityManager);
        NetworkInfo mockedNetworkInfo = Mockito.mock(NetworkInfo.class);
        shadowConnectivityManager.setActiveNetworkInfo(mockedNetworkInfo);
        Mockito.when(mockedNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        networkDetector = new NetworkDetector(RuntimeEnvironment.application);
        Field stateField = NetworkDetector.class.getDeclaredField("wifiState");
        stateField.setAccessible(true);
        Assert.assertEquals(WifiManager.WIFI_STATE_ENABLED, stateField.getInt(networkDetector));
        Mockito.when(mockedNetworkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);
        networkDetector = new NetworkDetector(RuntimeEnvironment.application);
        Assert.assertEquals(WifiManager.WIFI_STATE_DISABLED, stateField.getInt(networkDetector));
    }

    @Test
    public void testEnabled() {
        Assert.assertTrue(networkDetector.isEnabled());
    }

    @Test
    public void testAvailability() throws Exception {
        shadowWifiManager.setWifiEnabled(true);
        Assert.assertFalse(networkDetector.isAvailable());
        Field wifiStateField = NetworkDetector.class.getDeclaredField("wifiState");
        wifiStateField.setAccessible(true);
        wifiStateField.setInt(networkDetector, WifiManager.WIFI_STATE_ENABLED);
        Assert.assertTrue(networkDetector.isAvailable());
        shadowWifiManager.setWifiEnabled(false);
        Assert.assertFalse(networkDetector.isAvailable());
    }

    @Test
    public void testWifiStateChanging() throws Exception {
        Field wifiStateField = NetworkDetector.class.getDeclaredField("wifiState");
        wifiStateField.setAccessible(true);
        Assert.assertEquals(WifiManager.WIFI_STATE_DISABLED, wifiStateField.getInt(networkDetector));
        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_ENABLED);
        networkDetector.onReceiveData(intent);
        Assert.assertEquals(WifiManager.WIFI_STATE_ENABLED, wifiStateField.getInt(networkDetector));
        networkDetector.onReceiveData(null);
        Assert.assertEquals(WifiManager.WIFI_STATE_ENABLED, wifiStateField.getInt(networkDetector));
    }

    @Test
    public void testRunWithScanResult() throws Exception {
        long timestamp = System.currentTimeMillis();
        List<ScanResult> predeterminedResults = new ArrayList<>();
        ScanResult scanResult = Mockito.mock(ScanResult.class);
        scanResult.BSSID = "testBSSID";
        scanResult.level = 10;
        predeterminedResults.add(scanResult);
        shadowWifiManager.setScanResults(predeterminedResults);
        Field dataField = AbstractDetector.class.getDeclaredField("dataStored");
        dataField.setAccessible(true);
        Map<String, List<JsonArray>> data = (Map<String, List<JsonArray>>)dataField.get(networkDetector);
        Assert.assertEquals(0, data.get("default").size());
        networkDetector.run();
        data = (Map<String, List<JsonArray>>)dataField.get(networkDetector);
        Assert.assertEquals(1, data.get("default").size());
        JsonArray result = data.get("default").get(0);
        long timeReturned = result.get(0).getAsLong();
        Assert.assertTrue(timeReturned >= timestamp);
        Assert.assertEquals("testBSSID", result.get(1).getAsString());
        Assert.assertEquals(10, result.get(2).getAsInt());
    }
}