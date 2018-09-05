package com.telenor.possumcore.detectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.SystemClock;

import com.google.gson.JsonArray;
import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.TestUtils;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
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
import org.robolectric.shadows.ShadowSensorManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class GyroScopeTest {
    @Mock
    private Sensor mockedSensor;

    private GyroScope gyroScope;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        SensorManager sensorManager = (SensorManager) RuntimeEnvironment.application.getSystemService(Context.SENSOR_SERVICE);
        Assert.assertNotNull(sensorManager);
        ShadowSensorManager shadowSensorManager = Shadows.shadowOf(sensorManager);
        shadowSensorManager.addSensor(Sensor.TYPE_GYROSCOPE, mockedSensor);
        gyroScope = new GyroScope(RuntimeEnvironment.application);
    }

    @After
    public void tearDown() {
        gyroScope = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(gyroScope);
        Assert.assertEquals(DetectorType.Gyroscope, gyroScope.detectorType());
        Assert.assertEquals("gyroscope", gyroScope.detectorName());
    }

    @Test
    public void testOnSensorChanged() throws Exception {
        long presentTimestamp = System.currentTimeMillis();
        long nanoTimestamp = SystemClock.elapsedRealtime()*1000;
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(gyroScope);
        Assert.assertEquals(1, dataStored.size());
        Assert.assertEquals(0, dataStored.get("default").size());
        gyroScope.onSensorChanged(TestUtils.createSensorEvent(mockedSensor, nanoTimestamp, 0, 1, 2, 3));
        gyroScope.onSensorChanged(TestUtils.createSensorEvent(mockedSensor, nanoTimestamp, 0, 1, 2, 3));
        dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(gyroScope);
        Assert.assertEquals(1, dataStored.get("default").size());
        JsonArray stored = dataStored.get("default").get(0);
        Assert.assertTrue(stored.get(0).getAsLong() >= presentTimestamp);
    }
}