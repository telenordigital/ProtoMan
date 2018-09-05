package com.telenor.possumcore.abstractdetectors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.telenor.possumcore.interfaces.IDetectorChange;

/***
 * AbstractSensorDetector class that handles all detecting of sensor changes from the android
 * sensor manager. Note that OnSensorChanged is NOT implemented here, it will need to be
 * in all usages of this class. The important thing it will need to do is to is handle the
 * registering/unRegistering from the sensor manager.
 */
public abstract class AbstractSensorDetector extends AbstractDetector implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private int presentAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH;
    private static final int MIN_INTERVAL_MILLI = 35;
    private static final int MIN_INTERVAL_MICRO = MIN_INTERVAL_MILLI * 1000;
    private static final long MIN_INTERVAL_NANO = MIN_INTERVAL_MICRO * 1000;
    private long lastRecord;

    /**
     * Constructor for detectors using the built-in android sensorManager. Initializes a basic detector
     *
     * @param context a valid android context
     * @param sensorType the sensorType you want to listen to, found by Sensor.TYPE
     */
    public AbstractSensorDetector(@NonNull Context context, int sensorType, IDetectorChange listener) {
        super(context, listener);
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null)
            return;
        sensor = sensorManager.getDefaultSensor(sensorType);
    }

    @Override
    public void onResume(boolean continueRunning) {
        super.onResume(continueRunning);
        sensorManager.registerListener(this, sensor, MIN_INTERVAL_MICRO);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this, sensor);
    }

    /**
     * Checks whether timestamp has passed a minimum of milliseconds.
     *
     * @return true if it has passed the minimum, false if not
     */
    protected boolean isInvalid(SensorEvent event) {
        if (lastRecord == 0) {
            lastRecord = event.timestamp;
            return false;
        }
        if ((event.timestamp - lastRecord) <= MIN_INTERVAL_NANO) return true;
        lastRecord = event.timestamp;
        return false;
    }

    /**
     * Handles the registering of the listener as well as the actual running
     */
    @Override
    public void run() {
        super.run();
        sensorManager.registerListener(this, sensor, MIN_INTERVAL_MICRO);
    }

    /**
     * Yield the power in mA used by the sensor while in use
     *
     * @return power consumption in mA
     */
    @SuppressWarnings("unused")
    float powerUsage() {
        return sensor != null ? sensor.getPower() : -1;
    }

    /**
     * Termination calls unRegistering of the sensor from the sensorManager. It must be called to
     * ensure no memory leaks. It will be automatically called by the PossumCore when stopping
     */
    @Override
    public void terminate() {
        sensorManager.unregisterListener(this, sensor);
    }

    /**
     * This implementation of isEnabled relies on there being a sensor to startListening to. Should the
     * constructor fail to find the given sensor it throw a missing sensor exception and will not
     * enable this as a sensor. Consequently it will not allow any startListening
     *
     * @return whether the sensor is enabled
     */
    @Override
    public boolean isEnabled() {
        return sensor != null;
    }

    /**
     * Returns an estimated current time
     *
     * @param event the sensorevent you want to get timestamp from
     * @return the timestamp in epoch timestamp format
     */
    protected long timestamp(SensorEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return super.now() + ((event.timestamp - SystemClock.elapsedRealtimeNanos()) / 1000000L);
        } else return super.now() + ((event.timestamp - SystemClock.elapsedRealtime()*1000) /1000000L);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // TODO: Find a way to deal with massive accuracy changes, if it is a problem?
        presentAccuracy = i;
    }

    /**
     * Handy function for getting the present accuracy
     * @return the present accuracy represented with SensorManager.SENSOR_STATUS_*
     */
    int currentAccuracy() {
        return presentAccuracy;
    }
}