package com.telenor.possumcore.abstractdetectors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.gson.JsonArray;
import com.telenor.possumcore.LimitedConcurrentQueue;
import com.telenor.possumcore.interfaces.IDetectorChange;

import net.danlew.android.joda.JodaTimeAndroid;

import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Basic detector abstract, storing all needed components needed to detect and run. Even if not
 * every implementation of this will run but utilize a different method of sampling, it is still
 * the base.
 */
public abstract class AbstractDetector implements Runnable {
    private Context context;
    private String uniqueUserId;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    protected final Map<String, LimitedConcurrentQueue<JsonArray>> dataStored = new HashMap<>();
    protected static final String tag = AbstractDetector.class.getName();
    protected static final String defaultSet = "default";
    private IDetectorChange changeListener;

    /**
     * Constructor for all detectors. Initializes a basic detector
     *
     * @param context a valid android context
     */
    public AbstractDetector(Context context, IDetectorChange listener) {
        if (context == null) throw new IllegalArgumentException("Missing context");
        changeListener = listener;
        JodaTimeAndroid.init(context);
        createDataSet(defaultSet);
        this.context = context.getApplicationContext();
    }

    /**
     * Handles what type of lists are created for the dataSets
     *
     * @return a list of JsonArrays. Which type of list is up to the implementation.
     * Default is an ArrayList. If necessary, use concurrent lists.
     */
    private LimitedConcurrentQueue<JsonArray> createInternalList(String dataSet) {
        return new LimitedConcurrentQueue<>(queueLimit(dataSet));
    }

    /**
     * Creates an internal memory set for a given dataSet. One set is default, others can be
     * created as needed
     *
     * @param dataSet name of the dataSet, "default" is taken for the standard set
     */
    protected void createDataSet(@NonNull String dataSet) {
        dataStored.put(dataSet, createInternalList(dataSet));
    }

    /**
     * Queue limit pr dataSet
     * Can, and should, be overridden if there is a desire to change how much the fifo queue should
     * contains
     *
     * @param key dataSet name
     * @return number of inserts, dependent on detector
     */
    public abstract int queueLimit(@NonNull String key);

    /**
     * Handy method for getting a present timestamp
     *
     * @return long timestamp in millis
     */
    public long now() {
        return DateTime.now().getMillis();
    }

    /**
     * Whether the detector is enabled on the phone. This is usually a yes or no, depending on model
     * etc. The detector cannot be used if it is not enabled. All subclasses must check for its
     * respective confirmation of whether or not it exist on the phone. Default the detector is
     * enabled. All detectors must confirm this or it will be so.
     *
     * @return true if sensor is present, false if not present
     */
    public boolean isEnabled() {
        return true;
    }

    /**
     * Whether the detector is currently available (not same as enabled which is whether or not
     * the detector is something the phone has). Should be overridden by the subclassed detectors.
     *
     * @return true if available or false if not
     */
    public boolean isAvailable() {
        return isPermitted();
    }

    /**
     * The required permission to use the detector, if any. Must be overridden if a permission is
     * needed
     *
     * @return a manifest permission or null for none needed
     */
    protected String requiredPermission() {
        return null;
    }

    /**
     * Each sensor needs access to a context, the different implementation must take this into account
     *
     * @return the context the sensor supplies
     */
    public Context context() {
        return context;
    }

    /**
     * For detectors requiring access to certain privileges - like location or camera,
     *
     * @return whether the user has permitted the use of the sensor. Should be part of the
     * availability.
     */
    protected boolean isPermitted() {
        return requiredPermission() == null ||
                ContextCompat.checkSelfPermission(context(), requiredPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Sends the sensorUpdate to all listeners for sensor availability updates. This depends on
     * which structure is used. Service structure would need intents while interface is best
     * otherwise
     */
    public void detectorStatusChanged() {
        if (changeListener != null) {
            changeListener.detectorChanged(this);
        }
    }

    @Override
    public void run() {
        isRunning.set(true);
        for (LimitedConcurrentQueue<JsonArray> data : dataStored.values()) {
            data.clear();
        }
    }

    /**
     * Retrieves a given dataSet
     *
     * @param dataSet the name of the dataSet
     * @return a queue containing the data (in jsonArrays) or null if dataSet is not found
     */
    protected LimitedConcurrentQueue<JsonArray> dataSet(@NonNull String dataSet) {
        return dataStored.get(dataSet);
    }

    /**
     * Yields the detector type from the DetectorType class. Should correspond to match with
     * Sensor. Type and whichever own type of detector
     *
     * @return integer representing the type (Check DetectorType class)
     */
    public abstract int detectorType();

    /**
     * Official "name" of detector, a simple string using the english language. Should not be used
     * for official UI use - if so, use detectorType and map it to a resource name so it can be
     * localized
     *
     * @return string with "name" or "designation" of detector
     */
    public abstract String detectorName();

    /**
     * Defines the user id the detectors data refers to. Must be set before start listen will
     * activate
     *
     * @param uniqueUserId the user id
     */
    public void setUniqueUserId(String uniqueUserId) {
        this.uniqueUserId = uniqueUserId;
    }

    /**
     * Gets the unique user id
     *
     * @return a unique string identifying the user
     */
    public String getUserId() {
        return uniqueUserId;
    }

    /**
     * Handles writing data from detector to an outputStream. Note atm it only stores to internal
     * memory. Overridden methods will handle the rest
     *
     * @param data the data you want to write in the form of a jsonArray
     */
    protected void streamData(JsonArray data) {
        streamData(data, defaultSet);
    }

    protected void streamData(JsonArray data, String dataSet) {
        LimitedConcurrentQueue<JsonArray> set = dataSet(dataSet);
        if (set == null) {
            Log.e(tag, "DataSet is not found, data is not stored (" + dataSet + ")");
            return;
        }
        set.add(data);
//        Log.d(tag, "AP:" + data.toString());
    }

    /**
     * Handles what happens when you restart the library after a pause. Use this to restart
     * processes like camera or microphone.
     *
     * @param continueRunning whether to continue running or not
     */
    public void onResume(boolean continueRunning) {

    }

    /**
     * Handles what happens when you pause the library. Use this to stop processes like camera or
     * microphone etc from going while not supposed to go
     */
    public void onPause() {

    }

    /**
     * Retrieve all dataSets with all data stored
     *
     * @return a map with dataSet names containing jsonArrays with all the data
     */
    public Map<String, LimitedConcurrentQueue<JsonArray>> dataStored() {
        return dataStored;
    }

    /**
     * Clean up detector dependencies after runs/auths/data gatherings.
     */
    public void terminate() {
        isRunning.set(false);
    }

    /**
     * Confirms whether detector is currently running or if it has stopped
     *
     * @return true if still running, false if stopped
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Final cleanup when Awesome Possum is completely done. Only necessary for some detectors
     */
    public void cleanUp() {
    }

    /**
     * Gives the detectors stored data as a restful json object
     * Also clears data, so be careful. Make sure data is sent and not lost.
     * Locks system while it is retrieved so no concurrency is retrieved
     *
     * @return a jsonArray for all the data
     */
    public synchronized JsonArray jsonData(String dataSet) {
        JsonArray outputArr = new JsonArray();
        synchronized (dataStored) {
            LimitedConcurrentQueue<JsonArray> data = dataStored.get(dataSet);
            for (JsonArray arr : data) {
                outputArr.add(arr);
            }
            dataStored.put(dataSet, new LimitedConcurrentQueue<>(queueLimit(dataSet))); // Clearing the dataSet
        }
        return outputArr;
    }

    /**
     * Returns the keys of the data stored
     * @return a set of the keys
     */
    public synchronized Set<String> jsonKeys() {
        return new HashSet<>(dataStored.keySet());
    }
}