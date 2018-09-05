package com.telenor.possumcore;

import android.content.Context;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

import net.danlew.android.joda.JodaTimeAndroid;

import java.io.InputStream;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestUtils {
    public static void initializeJodaTime() {
        Context context = mock(Context.class);
        Resources resources = mock(Resources.class);
        when(resources.openRawResource(anyInt())).thenReturn(mock(InputStream.class));
        when(context.getResources()).thenReturn(resources);
        when(context.getApplicationContext()).thenReturn(context);
        JodaTimeAndroid.init(context);
    }
    public static SensorEvent createSensorEvent(Sensor sensor, long nanoTimestamp, int accuracy, float x, float y, float z) throws Exception {
        SensorEvent sensorEvent = mock(SensorEvent.class);
        sensorEvent.timestamp = nanoTimestamp;
        sensorEvent.sensor = sensor;
        sensorEvent.accuracy = accuracy;
        Field valuesField = SensorEvent.class.getField("values");
        valuesField.setAccessible(true);
        valuesField.set(sensorEvent, new float[]{x, y, z});
        return sensorEvent;
    }
}
