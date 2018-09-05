package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonArray;
import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.TestUtils;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.abstractdetectors.AbstractReceiverDetector;
import com.telenor.possumcore.constants.DetectorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLocationManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class LocationDetectorTest {
    @Mock
    private Context mockedContext;
    @Mock
    private LocationManager mockedLocationManager;
    @Mock
    private Handler mockedHandler;

    private ShadowLocationManager shadowLocationManager;
    private LocationDetector locationDetector;
    private int statusChangedCounter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestUtils.initializeJodaTime();
        statusChangedCounter = 0;
        when(mockedContext.getApplicationContext()).thenReturn(mockedContext);
        LocationManager locationManager = (LocationManager) RuntimeEnvironment.application.getSystemService(Context.LOCATION_SERVICE);
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(locationManager);
        Assert.assertNotNull(locationManager);
        shadowLocationManager = Shadows.shadowOf(locationManager);
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        locationDetector = new LocationDetector(mockedContext) {
            @Override
            public void detectorStatusChanged() {
                statusChangedCounter++;
            }
            @Override
            protected Handler getHandler() {
                return mockedHandler;
            }
        };
    }

    @After
    public void tearDown() {
        locationDetector = null;
    }

    @Test
    public void testIntentFilterIsCorrect() throws Exception {
        Field intentFilterField = AbstractReceiverDetector.class.getDeclaredField("intentFilter");
        intentFilterField.setAccessible(true);
        IntentFilter intentFilter = (IntentFilter)intentFilterField.get(locationDetector);
        Assert.assertEquals(1, intentFilter.countActions());
        Assert.assertEquals(LocationManager.PROVIDERS_CHANGED_ACTION, intentFilter.getAction(0));
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(locationDetector);
        Assert.assertEquals("position", locationDetector.detectorName());
        Assert.assertEquals(DetectorType.Position, locationDetector.detectorType());
        Assert.assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, locationDetector.requiredPermission());
    }

    @Test
    public void testEnabled() {
        Assert.assertTrue(locationDetector.isEnabled());
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(null);
        locationDetector = new LocationDetector(mockedContext);
        Assert.assertFalse(locationDetector.isEnabled());
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        when(mockedLocationManager.getAllProviders()).thenReturn(new ArrayList<>());
        locationDetector = new LocationDetector(mockedContext);
        Assert.assertFalse(locationDetector.isEnabled());
        List<String> fakeList = new ArrayList<>();
        fakeList.add(LocationManager.GPS_PROVIDER);
        when(mockedLocationManager.getAllProviders()).thenReturn(fakeList);
        locationDetector = new LocationDetector(mockedContext);
        Assert.assertTrue(locationDetector.isEnabled());
    }

    @Test
    public void testAvailabilityWhenStatusChanges() {
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        locationDetector.onStatusChanged(LocationManager.NETWORK_PROVIDER, LocationProvider.OUT_OF_SERVICE, null);
        Assert.assertEquals(1, statusChangedCounter);
        Assert.assertFalse(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        locationDetector.onStatusChanged(LocationManager.GPS_PROVIDER, LocationProvider.AVAILABLE, null);
        Assert.assertEquals(2, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        locationDetector.onStatusChanged(LocationManager.NETWORK_PROVIDER, LocationProvider.AVAILABLE, null);
        Assert.assertEquals(3, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        locationDetector.onStatusChanged(LocationManager.GPS_PROVIDER, LocationProvider.OUT_OF_SERVICE, null);
        Assert.assertEquals(4, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
    }

    @Test
    public void testLastLocation() throws Exception {
        Method lastLocationMethod = LocationDetector.class.getDeclaredMethod("lastLocation");
        lastLocationMethod.setAccessible(true);
        Location lastLocation = (Location) lastLocationMethod.invoke(locationDetector);
        Assert.assertNull(lastLocation);
        long gpsTimestamp = System.currentTimeMillis();
        Location location = fakeLocation(LocationManager.GPS_PROVIDER, 10, 60, gpsTimestamp);
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location);
        lastLocation = (Location) lastLocationMethod.invoke(locationDetector);
        Assert.assertNotNull(lastLocation);
        Assert.assertEquals(gpsTimestamp, lastLocation.getTime());
        long networkTimestamp = gpsTimestamp - 100;
        Location networkLocation = fakeLocation(LocationManager.NETWORK_PROVIDER, 20, 40, networkTimestamp);
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, networkLocation);
        lastLocation = (Location) lastLocationMethod.invoke(locationDetector);
        Assert.assertNotNull(lastLocation);
        Assert.assertEquals(networkTimestamp, lastLocation.getTime());
        long olderNetworkTimestamp = gpsTimestamp + 100;
        Location olderNetworkLocation = fakeLocation(LocationManager.NETWORK_PROVIDER, 20, 40, olderNetworkTimestamp);
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, olderNetworkLocation);
        lastLocation = (Location) lastLocationMethod.invoke(locationDetector);
        Assert.assertNotNull(lastLocation);
        Assert.assertEquals(gpsTimestamp, lastLocation.getTime());
    }

    @Test
    public void testOnLocationChanged() throws Exception {
        long timestamp = System.currentTimeMillis();
        long positionTimestamp = timestamp - 1000;
        Location location = fakeLocation(LocationManager.GPS_PROVIDER, 10, 60, positionTimestamp);
        location.setAltitude(15);
        location.setAccuracy(16);
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location);
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> data = (Map<String, List<JsonArray>>) dataStoredField.get(locationDetector);
        Assert.assertEquals(0, data.get("default").size());
        locationDetector.onLocationChanged(location);
        data = (Map<String, List<JsonArray>>) dataStoredField.get(locationDetector);
        Assert.assertEquals(1, data.get("default").size());
        JsonArray obj = data.get("default").get(0);
        Assert.assertTrue(timestamp <= obj.get(0).getAsLong());
//        Assert.assertEquals(positionTimestamp, obj.get(1).getAsLong());
        Assert.assertEquals(10, obj.get(1).getAsFloat(), 0);
        Assert.assertEquals(60, obj.get(2).getAsFloat(), 0);
        Assert.assertEquals(15, obj.get(3).getAsFloat(), 0);
        Assert.assertEquals(16, obj.get(4).getAsFloat(), 0);
        Assert.assertEquals(LocationManager.GPS_PROVIDER, obj.get(5).getAsString());
    }

    @Test
    public void testOnLocationIgnoresEmptyLocations() throws Exception {
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> data = (Map<String, List<JsonArray>>) dataStoredField.get(locationDetector);
        Assert.assertEquals(1, data.size());
        Assert.assertEquals(0, data.get("default").size());
        locationDetector.onLocationChanged(null);
        data = (Map<String, List<JsonArray>>) dataStoredField.get(locationDetector);
        Assert.assertEquals(0, data.get("default").size());
    }

    @Test
    public void testRunIgnoresNotAvailableOrEnabled() {
        when(mockedLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(false);
        when(mockedLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(false);
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        List<String> providers = new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        when(mockedLocationManager.getAllProviders()).thenReturn(providers);
        locationDetector = new LocationDetector(mockedContext);
        verify(mockedLocationManager, times(0)).getLastKnownLocation(Mockito.endsWith("gps"));
        Assert.assertFalse(locationDetector.isAvailable());
        locationDetector.run();
        verify(mockedLocationManager, times(0)).getLastKnownLocation(Mockito.endsWith("gps"));
        when(mockedLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true);
        Assert.assertTrue(locationDetector.isAvailable());
        locationDetector.run();
        verify(mockedLocationManager, times(1)).getLastKnownLocation(Mockito.endsWith("gps"));
        when(mockedLocationManager.getAllProviders()).thenReturn(new ArrayList<>());
        Assert.assertFalse(locationDetector.isEnabled());
        locationDetector.run();
        verify(mockedLocationManager, times(1)).getLastKnownLocation(Mockito.endsWith("gps"));
    }

    @Test
    public void testRunStartsRequestIfMissingLastOrLongerThanTenMinutesAgo() {
        List<String> providers = new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        when(mockedLocationManager.getAllProviders()).thenReturn(providers);
        when(mockedLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);
        when(mockedLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true);
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        locationDetector = new LocationDetector(mockedContext);
        Assert.assertTrue(locationDetector.isEnabled());
        Assert.assertTrue(locationDetector.isAvailable());
        // Null lastLocation
        verify(mockedLocationManager, times(0)).requestSingleUpdate(Mockito.matches("gps"), any(LocationListener.class), any(Looper.class));
        verify(mockedLocationManager, times(0)).requestSingleUpdate(Mockito.matches("network"), any(LocationListener.class), any(Looper.class));
        locationDetector.run();
        verify(mockedLocationManager, times(1)).requestSingleUpdate(Mockito.matches("gps"), any(LocationListener.class), any(Looper.class));
        verify(mockedLocationManager, times(1)).requestSingleUpdate(Mockito.matches("network"), any(LocationListener.class), any(Looper.class));

        // LastLocation not null but too long ago
        long presentTimestamp = System.currentTimeMillis()-10;
        long oldTimestamp = presentTimestamp - 20 * 60 * 1000;
        Location validLocation = fakeLocation(LocationManager.GPS_PROVIDER, 10, 60, oldTimestamp);
        when(mockedLocationManager.getLastKnownLocation(anyString())).thenReturn(validLocation);
        locationDetector.run();
        verify(mockedLocationManager, times(2)).requestSingleUpdate(Mockito.matches("gps"), any(LocationListener.class), any(Looper.class));
        verify(mockedLocationManager, times(2)).requestSingleUpdate(Mockito.matches("network"), any(LocationListener.class), any(Looper.class));

        // LastLocation not null and short while ago
        Location invalidLocation = fakeLocation(LocationManager.GPS_PROVIDER, 10, 60, presentTimestamp);
        when(mockedLocationManager.getLastKnownLocation(anyString())).thenReturn(invalidLocation);
        locationDetector.run();
        verify(mockedLocationManager, times(2)).requestSingleUpdate(Mockito.matches("gps"), any(LocationListener.class), any(Looper.class));
        verify(mockedLocationManager, times(2)).requestSingleUpdate(Mockito.matches("network"), any(LocationListener.class), any(Looper.class));
    }

    @Test
    public void testUsingLastLocationAsDataWhenWithinTimeLimit() throws Exception {
        List<String> providers = new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        when(mockedLocationManager.getAllProviders()).thenReturn(providers);
        when(mockedLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);
        when(mockedLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true);
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        locationDetector = new LocationDetector(mockedContext);

        long timestamp = System.currentTimeMillis() - 100;
        Location validLocation = fakeLocation(LocationManager.GPS_PROVIDER, 10, 60, timestamp);
        when(mockedLocationManager.getLastKnownLocation(anyString())).thenReturn(validLocation);

        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(locationDetector);
        Assert.assertTrue(dataStored.get("default").size() == 0);
        locationDetector.run();
        dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(locationDetector);
        Assert.assertTrue(dataStored.get("default").size() == 1);
        JsonArray data = dataStored.get("default").get(0);
        Assert.assertTrue(timestamp <= data.get(0).getAsLong());
    }

    @Test
    public void testProvidersEnabledAndDisabled() {
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        locationDetector.onProviderDisabled(LocationManager.NETWORK_PROVIDER);
        Assert.assertEquals(1, statusChangedCounter);
        Assert.assertFalse(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true);
        locationDetector.onProviderEnabled(LocationManager.NETWORK_PROVIDER);
        Assert.assertEquals(2, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true);
        locationDetector.onProviderEnabled(LocationManager.GPS_PROVIDER);
        Assert.assertEquals(3, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false);
        locationDetector.onProviderDisabled(LocationManager.NETWORK_PROVIDER);
        Assert.assertEquals(4, statusChangedCounter);
        Assert.assertTrue(locationDetector.isAvailable());
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false);
        locationDetector.onProviderDisabled(LocationManager.GPS_PROVIDER);
        Assert.assertEquals(5, statusChangedCounter);
        Assert.assertFalse(locationDetector.isAvailable());
    }

    @Test
    public void testReceivingProvidersChangedIntentFiresUpdate() {
        Intent intent = new Intent(LocationManager.PROVIDERS_CHANGED_ACTION);
        Assert.assertEquals(0, statusChangedCounter);
        locationDetector.onReceiveData(intent);
        Assert.assertEquals(1, statusChangedCounter);
    }

    @Test
    public void testRequestPositionMethod() {
        List<String> providers = new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        when(mockedLocationManager.getAllProviders()).thenReturn(providers);
        when(mockedLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)).thenReturn(true);
        when(mockedLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)).thenReturn(true);
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        locationDetector = new LocationDetector(mockedContext);
        verify(mockedLocationManager, times(0)).requestSingleUpdate(matches("gps"), any(LocationListener.class), any(Looper.class));
        locationDetector.requestProviderPositions(LocationManager.GPS_PROVIDER);
        verify(mockedLocationManager, times(1)).requestSingleUpdate(matches("gps"), any(LocationListener.class), any(Looper.class));
    }

    @Test
    public void testTerminateRemovesUpdates() {
        when(mockedContext.getSystemService(Context.LOCATION_SERVICE)).thenReturn(mockedLocationManager);
        locationDetector = new LocationDetector(mockedContext);
        verify(mockedLocationManager, times(0)).removeUpdates(any(LocationListener.class));
        locationDetector.terminate();
        verify(mockedLocationManager, times(1)).removeUpdates(any(LocationListener.class));
    }

    private Location fakeLocation(String provider, float latitude, float longitude, long timestamp) {
        Location location = new Location(provider);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setTime(timestamp);
        return location;
    }
}