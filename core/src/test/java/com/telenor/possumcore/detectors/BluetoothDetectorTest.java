package com.telenor.possumcore.detectors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

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
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBluetoothAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class BluetoothDetectorTest {
    @Mock
    private Context mockedContext;
    @Mock
    private BluetoothManager mockedBluetoothManager;
    @Mock
    private BluetoothAdapter mockedBluetoothAdapter;
    @Mock
    private BluetoothLeScanner mockedBluetoothLEScanner;

    private ShadowBluetoothAdapter shadowBluetoothAdapter;
    private BluetoothDetector bluetoothDetector;
    private BluetoothAdapter bluetoothAdapter;
    private int counter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        counter = 0;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        shadowBluetoothAdapter = Shadows.shadowOf(ShadowBluetoothAdapter.getDefaultAdapter());
        shadowBluetoothAdapter.enable();
        when(mockedContext.getApplicationContext()).thenReturn(mockedContext);
        when(mockedContext.getSystemService(Context.BLUETOOTH_SERVICE)).thenReturn(mockedBluetoothManager);
        when(mockedBluetoothManager.getAdapter()).thenReturn(mockedBluetoothAdapter);
        bluetoothDetector = new BluetoothDetector(RuntimeEnvironment.application) {
            @Override
            public void detectorStatusChanged() {
                counter++;
            }
        };
    }

    @After
    public void tearDown() {
        bluetoothDetector = null;
    }

    @Test
    public void testInitialValues() {
        Assert.assertEquals("bluetooth", bluetoothDetector.detectorName());
        Assert.assertEquals(DetectorType.Bluetooth, bluetoothDetector.detectorType());
    }

    @Test
    public void testAdapterIsEqualToSetup() throws Exception {
        Field bluetoothAdapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        bluetoothAdapterField.setAccessible(true);
        Assert.assertSame(bluetoothAdapter, bluetoothAdapterField.get(bluetoothDetector));
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Test
    public void testPreLollipopInitialize() throws Exception {
        Assert.assertNotNull(bluetoothDetector);
        Field bleCallbackField = BluetoothDetector.class.getDeclaredField("bleScanCallback");
        bleCallbackField.setAccessible(true);
        Assert.assertNull(bleCallbackField.get(bluetoothDetector));
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testPostLollipopInitialize() throws Exception {
        when(mockedBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mockedBluetoothLEScanner);
        bluetoothDetector = new BluetoothDetector(RuntimeEnvironment.application);
        Assert.assertNotNull(bluetoothDetector);
        Field bleCallbackField = BluetoothDetector.class.getDeclaredField("bleScanCallback");
        bleCallbackField.setAccessible(true);
        Assert.assertNotNull(bleCallbackField.get(bluetoothDetector));
    }

    @Test
    public void testReceiverIsTurnedOnByDefault() throws Exception {
        Field alwaysField = AbstractReceiverDetector.class.getDeclaredField("isAlwaysOn");
        alwaysField.setAccessible(true);
        verify(mockedContext, never()).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        bluetoothDetector = new BluetoothDetector(mockedContext);
        verify(mockedContext, times(1)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        Assert.assertTrue((Boolean)alwaysField.get(bluetoothDetector));
    }

    @Test
    public void testIsEnabled() throws Exception {
        Assert.assertTrue(bluetoothDetector.isEnabled());
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        Assert.assertNotNull(adapterField.get(bluetoothDetector));

        adapterField.set(bluetoothDetector, null);
        Assert.assertFalse(bluetoothDetector.isEnabled());
    }

    @Test
    public void testIsAvailable() {
        Assert.assertTrue(bluetoothDetector.isAvailable());
        shadowBluetoothAdapter.disable();
        Assert.assertFalse(bluetoothDetector.isAvailable());
    }

    @Test
    public void testLongScanUnavailableByDefault() throws Exception {
        Method scanDoableMethod = AbstractDetector.class.getDeclaredMethod("isLongScanDoable");
        scanDoableMethod.setAccessible(true);
        Assert.assertFalse((Boolean)scanDoableMethod.invoke(bluetoothDetector));
    }

    @Test
    public void testIntentFilterIsCorrect() throws Exception {
        Field intentFilterField = AbstractReceiverDetector.class.getDeclaredField("intentFilter");
        intentFilterField.setAccessible(true);
        IntentFilter storedFilter = (IntentFilter)intentFilterField.get(bluetoothDetector);
        Assert.assertEquals(2, storedFilter.countActions());
        Assert.assertEquals(BluetoothAdapter.ACTION_STATE_CHANGED, storedFilter.getAction(0));
        Assert.assertEquals(BluetoothDevice.ACTION_FOUND, storedFilter.getAction(1));
    }

    @Test
    public void testAdditionalDataSetCreated() throws Exception {
        Field dataField = AbstractDetector.class.getDeclaredField("dataStored");
        dataField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataField.get(bluetoothDetector);
        Assert.assertEquals(2, dataStored.size());
        Assert.assertNotNull(dataStored.get("default"));
        Assert.assertNotNull(dataStored.get("bluetooth_scan"));
    }

    @Test
    public void testConnectedOnlyRun() throws Exception {
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);
        Assert.assertTrue(bluetoothDetector.isEnabled());
        Assert.assertTrue(bluetoothDetector.isAvailable());
        verify(mockedBluetoothAdapter, never()).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), anyInt());
        bluetoothDetector.run();
        verify(mockedBluetoothAdapter, times(1)).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), eq(BluetoothProfile.HEADSET));
        verify(mockedBluetoothAdapter, times(1)).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), eq(BluetoothProfile.GATT));
        verify(mockedBluetoothAdapter, times(1)).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), eq(BluetoothProfile.GATT_SERVER));
        verify(mockedBluetoothAdapter, times(1)).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), eq(BluetoothProfile.A2DP));
        verify(mockedBluetoothAdapter, times(1)).getProfileProxy(any(Context.class), any(BluetoothProfile.ServiceListener.class), eq(BluetoothProfile.HEALTH));
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testBLEScanOnRunWithLongScanAvailable() throws Exception {
        when(mockedBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mockedBluetoothLEScanner);
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);
        verify(mockedBluetoothLEScanner, never()).startScan(eq(null), any(ScanSettings.class), any(ScanCallback.class));
        Assert.assertTrue(bluetoothDetector.isEnabled());
        Assert.assertTrue(bluetoothDetector.isAvailable());
        bluetoothDetector.run();
        verify(mockedBluetoothLEScanner, times(1)).startScan(eq(null), any(ScanSettings.class), any(ScanCallback.class));
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Test
    public void testNormalScanOnRunWithLongScanAvailable() throws Exception {
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mockedBluetoothAdapter.isDiscovering()).thenReturn(false);
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);

        verify(mockedBluetoothAdapter, never()).startDiscovery();
        bluetoothDetector.run();
        verify(mockedBluetoothAdapter, times(1)).startDiscovery();
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testRegularBluetoothOnNewerSdkRunsCorrect() throws Exception {
        when(mockedBluetoothAdapter.getBluetoothLeScanner()).thenReturn(null);
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mockedBluetoothAdapter.isDiscovering()).thenReturn(false);
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);

        verify(mockedBluetoothAdapter, never()).startDiscovery();
        bluetoothDetector.run();
        verify(mockedBluetoothAdapter, times(1)).startDiscovery();
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testTerminateStopsBLEScan() throws Exception {
        when(mockedBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mockedBluetoothLEScanner);
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        verify(mockedBluetoothLEScanner, never()).stopScan(any(ScanCallback.class));
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);

        bluetoothDetector.terminate();
        verify(mockedBluetoothLEScanner, times(1)).stopScan(any(ScanCallback.class));
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Test
    public void testTerminateStopsRegularDiscovery() throws Exception {
        when(mockedBluetoothAdapter.isEnabled()).thenReturn(true);
        when(mockedBluetoothAdapter.isDiscovering()).thenReturn(true);
        verify(mockedBluetoothAdapter, never()).cancelDiscovery();
        bluetoothDetector = new BluetoothDetector(mockedContext);
        Field adapterField = BluetoothDetector.class.getDeclaredField("bluetoothAdapter");
        adapterField.setAccessible(true);
        adapterField.set(bluetoothDetector, mockedBluetoothAdapter);
        bluetoothDetector.terminate();
        verify(mockedBluetoothAdapter, times(1)).cancelDiscovery();
    }

    @Test
    public void testReceiveIntentRequestsStatusChange() {
        Assert.assertEquals(0, counter);
        bluetoothDetector.onReceiveData(new Intent("meh"));
        Assert.assertEquals(1, counter);
        bluetoothDetector.onReceiveData(null);
        Assert.assertEquals(1, counter);
    }

    @Test
    public void testCallbackFromPairedDevices() throws Exception {
        long timestamp = System.currentTimeMillis();
        Field serviceListenerField = BluetoothDetector.class.getDeclaredField("serviceListener");
        serviceListenerField.setAccessible(true);
        BluetoothProfile.ServiceListener listener = (BluetoothProfile.ServiceListener)serviceListenerField.get(bluetoothDetector);
        listener.onServiceDisconnected(BluetoothProfile.HEALTH); // Just for completion
        BluetoothProfile mockedProxy = mock(BluetoothProfile.class);
        List<BluetoothDevice> fakeList = new ArrayList<>();
        BluetoothDevice mockedDevice = mock(BluetoothDevice.class);
        // Implement whens for mocked device
        when(mockedDevice.getType()).thenReturn(1);
        String fakeAddress = "fa:ke:ad:dr:es";
        when(mockedDevice.getAddress()).thenReturn(fakeAddress);
        BluetoothClass mockedBluetoothClass = mock(BluetoothClass.class);
        when(mockedDevice.getBluetoothClass()).thenReturn(mockedBluetoothClass);
        when(mockedBluetoothClass.getDeviceClass()).thenReturn(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE);
        fakeList.add(mockedDevice);
        when(mockedProxy.getDevicesMatchingConnectionStates(any())).thenReturn(fakeList);
        listener.onServiceConnected(BluetoothProfile.HEADSET, mockedProxy);


        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(bluetoothDetector);
        Assert.assertEquals(2, dataStored.size());
        List<JsonArray> dataList= dataStored.get("default");
        Assert.assertEquals(1, dataList.size());
        JsonArray data = dataList.get(0);
        Assert.assertTrue(timestamp < data.get(0).getAsLong());
        Assert.assertEquals(1, data.get(1).getAsInt());
        Assert.assertEquals(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE, data.get(2).getAsInt());
        Assert.assertEquals(fakeAddress, data.get(3).getAsString());
        Assert.assertEquals(BluetoothProfile.HEADSET, data.get(5).getAsInt());
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testCallbackFromBLEScan() throws Exception {
        long timestamp = System.currentTimeMillis();
        Field bleCallbackField = BluetoothDetector.class.getDeclaredField("bleScanCallback");
        bleCallbackField.setAccessible(true);
        ScanCallback scanCallback = (ScanCallback)bleCallbackField.get(bluetoothDetector);
        BluetoothDevice mockedDevice = mock(BluetoothDevice.class);
        BluetoothClass mockedBluetoothClass = mock(BluetoothClass.class);
        when(mockedDevice.getBluetoothClass()).thenReturn(mockedBluetoothClass);
        when(mockedBluetoothClass.getDeviceClass()).thenReturn(BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE);
        ScanRecord scanRecord = mock(ScanRecord.class);
        ScanResult scanResult = new ScanResult(mockedDevice, scanRecord, 10, timestamp);
        scanCallback.onScanResult(0, scanResult);

        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(bluetoothDetector);
        Assert.assertEquals(2, dataStored.size());
        List<JsonArray> dataList= dataStored.get("bluetooth_scan");
        Assert.assertEquals(1, dataList.size());
        JsonArray data = dataList.get(0);
        Assert.assertNotNull(data.get(0));
        // TODO: Test values for validity
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testNoTxReturnsMinInt() {
        // TODO: Implement
    }

    @Test
    public void testRegularScanReturns() {
        // TODO: Implement
    }

    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    @Test
    public void testBLEScanStopsAfterMaxTime() {
        // TODO: Implement
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Test
    public void testRegularScanStopsAfterMaxTime() {
        // TODO: Implement
    }
}