package com.telenor.possumcore.detectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumcore.abstractdetectors.AbstractSensorDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

/**
 * Uses accelerometer to determine the movement/gait of the user, as well as detecting motion
 * and how the phone is held
 */
public class Accelerometer extends AbstractSensorDetector {
    public Accelerometer(@NonNull Context context) {
        this(context, null);
    }
    public Accelerometer(@NonNull Context context, IDetectorChange listener) {
        super(context, Sensor.TYPE_ACCELEROMETER, listener);
    }

    @Override
    public int detectorType() {
        return DetectorType.Accelerometer;
    }

    @Override
    public String detectorName() {
        return "accelerometer";
    }

    @Override
    public int queueLimit(@NonNull String key) {
        return 85; // Default set - 3 seconds with one each 35 milliseconds
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