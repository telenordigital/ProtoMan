package com.telenor.possumcore.abstractdetectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.NonNull;

import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.TestUtils;
import com.telenor.possumcore.interfaces.IDetectorChange;

import org.joda.time.DateTime;
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
import org.robolectric.shadows.ShadowSensorManager;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class AbstractSensorDetectorTest {
    private AbstractSensorDetector abstractSensorDetector;
    @Mock
    private Context mockedContext;
    @Mock
    private Sensor mockedSensor;
    @Mock
    private SensorManager mockedSensorManager;
    @Mock
    private IDetectorChange detectorChange;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestUtils.initializeJodaTime();
        when(mockedContext.getApplicationContext()).thenReturn(mockedContext);
        when(mockedContext.getSystemService(Context.SENSOR_SERVICE)).thenReturn(mockedSensorManager);        when(mockedSensorManager.getDefaultSensor(anyInt())).thenReturn(mockedSensor);
        SensorManager sensorManager = (SensorManager) RuntimeEnvironment.application.getSystemService(Context.SENSOR_SERVICE);
        Assert.assertNotNull(sensorManager);
        ShadowSensorManager shadowSensorManager = Shadows.shadowOf(sensorManager);
        shadowSensorManager.addSensor(Sensor.TYPE_ACCELEROMETER, mockedSensor);
        abstractSensorDetector = new AbstractSensorDetector(RuntimeEnvironment.application, Sensor.TYPE_ACCELEROMETER, detectorChange) {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
    }

    @After
    public void tearDown() {
        abstractSensorDetector = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(abstractSensorDetector);
    }

    @Test
    public void testEnabled() {
        Assert.assertTrue(abstractSensorDetector.isEnabled());
    }

    @Test
    public void testUnableToFindSensorManager() {
        when(mockedContext.getSystemService(Context.SENSOR_SERVICE)).thenReturn(null);
        abstractSensorDetector = new AbstractSensorDetector(mockedContext, Sensor.TYPE_ACCELEROMETER, detectorChange) {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        Assert.assertFalse(abstractSensorDetector.isEnabled());
    }

    @Test
    public void testUnableToFindSensor() {
        when(mockedSensorManager.getDefaultSensor(anyInt())).thenReturn(null);
        abstractSensorDetector = new AbstractSensorDetector(mockedContext, Sensor.TYPE_ACCELEROMETER, detectorChange) {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        Assert.assertFalse(abstractSensorDetector.isEnabled());
    }

    @Config(sdk = Build.VERSION_CODES.JELLY_BEAN)
    @Test
    public void testTimestampOnVeryOldAndroid() throws Exception {
        long timestamp = abstractSensorDetector.timestamp(TestUtils.createSensorEvent(Mockito.mock(Sensor.class), System.currentTimeMillis(), 10, 10, 10, 10));
        Assert.assertTrue(timestamp > 0);
    }

    @Test
    public void testPowerUsageCheck() {
        when(mockedSensor.getPower()).thenReturn(10f);
        Assert.assertEquals(10f, abstractSensorDetector.powerUsage(), 0);
    }

    @Test
    public void testRunRegistersListener() {
        abstractSensorDetector = new AbstractSensorDetector(mockedContext, Sensor.TYPE_ACCELEROMETER, detectorChange) {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        verify(mockedSensorManager, times(0)).registerListener(any(SensorEventListener.class), any(Sensor.class), anyInt());
        abstractSensorDetector.run();
        verify(mockedSensorManager, times(1)).registerListener(any(SensorEventListener.class), any(Sensor.class), anyInt());
    }

    @Test
    public void testDetectorChangeStatus() {
        verify(detectorChange, times(0)).detectorChanged(any(AbstractSensorDetector.class));
        abstractSensorDetector.detectorStatusChanged();
        verify(detectorChange, times(1)).detectorChanged(any(AbstractSensorDetector.class));
    }

    @Test
    public void testTerminateRemovesListener() {
        abstractSensorDetector = new AbstractSensorDetector(mockedContext, Sensor.TYPE_ACCELEROMETER, detectorChange) {
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        verify(mockedSensorManager, times(0)).unregisterListener(any(SensorEventListener.class), any(Sensor.class));
        abstractSensorDetector.terminate();
        verify(mockedSensorManager, times(1)).unregisterListener(any(SensorEventListener.class), any(Sensor.class));
    }

    @Test
    public void testSensorOnlyPicksUpEveryThirtyFiveMillis() throws Exception {
        long timestamp = System.nanoTime();
        Assert.assertFalse(abstractSensorDetector.isInvalid(TestUtils.createSensorEvent(mockedSensor, timestamp, 0, 0.1f, 0.1f, 0.1f)));
        timestamp++;
        Assert.assertTrue(abstractSensorDetector.isInvalid(TestUtils.createSensorEvent(mockedSensor, timestamp, 0, 0.1f, 0.1f, 0.1f)));
        timestamp += +35000000L;
        Assert.assertFalse(abstractSensorDetector.isInvalid(TestUtils.createSensorEvent(mockedSensor, timestamp, 0, 0.1f, 0.1f, 0.1f)));
    }

    @Config(sdk=Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Test
    public void testTimestampFromSensorEventOnApi17AndAbove() throws Exception {
        long timestamp = DateTime.now().getMillis();
        SensorEvent event = TestUtils.createSensorEvent(mockedSensor, timestamp, 0, 0.1f, 0.1f, 0.1f);
        long timestampOut = abstractSensorDetector.timestamp(event);
        Assert.assertTrue(timestamp < timestampOut);
    }

    @Test
    public void testTimestampFromSensorEventOnApi16() throws Exception {
        long timestamp = DateTime.now().getMillis();
        SensorEvent event = TestUtils.createSensorEvent(mockedSensor, timestamp, 0, 0.1f, 0.1f, 0.1f);
        long timestampOut = abstractSensorDetector.timestamp(event);
        Assert.assertTrue(timestamp < timestampOut);
    }

    @Test
    public void testAccuracyChanged() throws Exception {
        Field accuracyField = AbstractSensorDetector.class.getDeclaredField("presentAccuracy");
        accuracyField.setAccessible(true);
        Assert.assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, accuracyField.getInt(abstractSensorDetector));
        abstractSensorDetector.onAccuracyChanged(mockedSensor, SensorManager.SENSOR_STATUS_ACCURACY_LOW);
        Assert.assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_LOW, accuracyField.getInt(abstractSensorDetector));
    }

    @Test
    public void testGetAccuracy() {
        Assert.assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, abstractSensorDetector.currentAccuracy());
    }
}