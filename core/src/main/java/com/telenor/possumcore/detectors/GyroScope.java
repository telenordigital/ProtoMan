package com.telenor.possumcore.detectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.os.Build;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumcore.abstractdetectors.AbstractSensorDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

/**
 * Detects changes in the gyroscope, in effect how the phone is held/lies
 */
public class GyroScope extends AbstractSensorDetector {
    public GyroScope(@NonNull Context context) {
        this(context, null);
    }
    public GyroScope(@NonNull Context context, IDetectorChange listener) {
        super(context, Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2?Sensor.TYPE_GYROSCOPE_UNCALIBRATED:Sensor.TYPE_GYROSCOPE, listener);
    }

    @Override
    public int detectorType() {
        return DetectorType.Gyroscope;
    }

    @Override
    public String detectorName() {
        return "gyroscope";
    }

    @Override
    public int queueLimit(@NonNull String key) {
        return 85; // Default set - 3 seconds with one each 50 milliseconds
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (isInvalid(sensorEvent)) return;
        JsonArray data = new JsonArray();
        data.add(""+timestamp(sensorEvent));
        data.add(""+sensorEvent.values[0]);
        data.add(""+sensorEvent.values[1]);
        data.add(""+sensorEvent.values[2]);
        streamData(data);
    }
}