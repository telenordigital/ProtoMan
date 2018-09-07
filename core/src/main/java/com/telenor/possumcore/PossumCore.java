package com.telenor.possumcore;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.Constants;
import com.telenor.possumcore.constants.CoreStatus;
import com.telenor.possumcore.detectors.Accelerometer;
import com.telenor.possumcore.detectors.AmbientSoundDetector;
import com.telenor.possumcore.detectors.BluetoothDetector;
import com.telenor.possumcore.detectors.GyroScope;
import com.telenor.possumcore.detectors.HardwareDetector;
import com.telenor.possumcore.detectors.ImageDetector;
import com.telenor.possumcore.detectors.LocationDetector;
import com.telenor.possumcore.detectors.NetworkDetector;
import com.telenor.possumcore.interfaces.IDetectorChange;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The core component for gathering data. This is the foundation for the Awesome Possum library.
 * It handles the detectors, starting them, stopping them and the subclasses handles whatever is
 * done with the data.
 */
public abstract class PossumCore implements IDetectorChange {
    private Set<AbstractDetector> detectors = new HashSet<>();
    private Handler handler = new Handler();
    private AtomicInteger status = new AtomicInteger(CoreStatus.Idle);
    private String userId;
    private ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "PossumProcessing");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private List<IDetectorChange> changeListeners = new ArrayList<>();
    private AtomicBoolean deniedCamera = new AtomicBoolean(false);
    private long timeOut = 3000; // Default timeOut

    private static final String tag = PossumCore.class.getName();

    /**
     * Constructor for the PossumCore
     *
     * @param context      a valid android context
     * @param uniqueUserId the unique identifier of whoever this session will gather data from
     */
    public PossumCore(@NonNull Context context, @NonNull String uniqueUserId) {
        addAllDetectors(context);
        for (AbstractDetector detector : detectors)
            detector.setUniqueUserId(uniqueUserId);
        userId = uniqueUserId;
    }

    /**
     * Adds an entry to the log
     *
     * @param context   a valid android context
     * @param text      the text stored
     */
    public static void addLogEntry(@NonNull Context context, String text) {
        Intent intent = new Intent(Constants.PossumLog);
        intent.putExtra("action", "add");
        intent.putExtra("time", System.currentTimeMillis());
        intent.putExtra("log", text);
        context.sendBroadcast(intent);
    }

    /**
     * Add a detector to the list
     *
     * @param detector a detector to gather/authenticate data from
     */
    protected void addDetector(AbstractDetector detector) {
        detectors.add(detector);
    }

    /**
     * Method for quickly adding the relevant detectors, must be overridden
     */
    protected void addAllDetectors(Context context) {
        addDetector(new HardwareDetector(context, this));
        addDetector(new Accelerometer(context, this));
        addDetector(new AmbientSoundDetector(context, this));
        addDetector(new GyroScope(context, this));
        addDetector(new NetworkDetector(context, this));
        addDetector(new LocationDetector(context, this));
        addDetector(new ImageDetector(context, "tensorflow_facerecognition.pb", this));
        addDetector(new BluetoothDetector(context, this));
    }

    /**
     * Starts gathering data. Will not access image or sound if program has requested these to not
     * be called.
     *
     * @return false if no detectors available to start or already listening, else true
     */
    public boolean startListening() {
        if (status.get() == CoreStatus.Running || detectors == null || detectors.size() == 0)
            return false;
        // Question: What happens if it is paused or processing? Should it start a new data set?
//        Log.i(tag, "AP: Start Listening");
        for (AbstractDetector detector : detectors) {
            if (detector instanceof ImageDetector && deniedCamera.get())
                continue;
            executorService.submit(detector);
        }
        status.set(CoreStatus.Running);
        if (timeOut > 0) {
            handler.postDelayed(this::stopListening, timeOut);
        }
        return true;
    }

    /**
     * Sets the status according to CoreStatus constants
     *
     * @param newStatus the new status
     */
    protected void setStatus(int newStatus) {
        status.set(newStatus);
    }

    /**
     * Returns a synchronized status of the core system
     *
     * @return a CoreStatus integer
     */
    protected int getStatus() {
        return status.get();
    }

    /**
     * Handles all changes in configuration from the app. This must be overridden in the activity
     * used and passed down to the possumCore
     *
     * @param newConfig the new configuration it is in
     */
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(tag, "AP: New Configuration:" + newConfig);
        // TODO: Need a working configChanged representation
    }

    /**
     * Handles pausing of all detectors while the app is for some reason closed down - for example
     * a phone call interrupting or the user
     */
    public void onPause() {
        switch (status.get()) {
            case CoreStatus.Processing:
            case CoreStatus.Running:
                // Correctly (presumably) called pause when exiting app.
                Log.i(tag, "AP: onPause");
                stopListening();
                setStatus(CoreStatus.Paused);
                break;
        }
    }

    public void detectorChanged(AbstractDetector detector) {
        for (IDetectorChange listener : changeListeners) {
            listener.detectorChanged(detector);
        }
    }

    /**
     * Handles an effective restart of eventual paused app due to interruption in progress
     */
    public void onResume() {
        if (status.get() == CoreStatus.Paused) {
            Log.i(tag, "AP: OnResume");
            // Ez way: startListening() - causes problems with deleting data set gathered so far?
            startListening();
        }
    }

    /**
     * A handy way to get the version of the possumCore library
     *
     * @param context a valid android context
     * @return a string representing the current version of the library
     */
    public static String version(@NonNull Context context) {
        return context.getString(R.string.possum_core_version_name);
    }

    /**
     * Method for retrieving the needed permissions. Will not be called if no permissions are
     * missing. If an app using the sdk wants the sdk to start when permissions are granted, they
     * should override the onRequestPermissionsResult in the activity
     *
     * @param activity an android activity
     */
    public void requestNeededPermissions(@NonNull Activity activity) {
        if (hasMissingPermissions(activity)) {
            ActivityCompat.requestPermissions(activity, missingPermissions(activity).toArray(new String[]{}), Constants.PermissionsRequestCode);
        }
    }

    /**
     * Prevents image detector from being used. This due to an issue causing pre-lollipop phones
     * (api 21) to not be able to detect whether camera is in use or not. As a consequence, before
     * any video conferences or camera uses, this method should be called to prevent it from
     * listening in on these sensors when this is needed.
     */
    public void denyCamera() {
        if (deniedCamera.get()) return;
        deniedCamera.set(true);
        for (AbstractDetector detector : detectors) {
            if (detector instanceof ImageDetector) {
                detector.terminate();
            }
        }
    }

    /**
     * Allows image detector to be used. This due to an issue causing pre-lollipop phones (api 21)
     * to not be able to detect whether camera is in use or not. As a consequence, after any video
     * conferences or camera uses, this method should be called to allow it to listen in on this
     * sensor when this is needed.
     * <p>
     * Note: This method only needs to be called if you previously denied the camera
     */
    public void allowCamera() {
        if (!deniedCamera.get()) return;
        deniedCamera.set(false);
        if (isListening()) {
            for (AbstractDetector detector : detectors) {
                if (detector instanceof ImageDetector) {
                    // Submit if already denied
                    executorService.submit(detector);
                }
            }
        }
    }

    /**
     * Quick way to access the detectors registered from subclasses
     *
     * @return a set with the detectors
     */
    public Set<AbstractDetector> detectors() {
        return detectors;
    }

    /**
     * The present list of dangerous permissions. Can be extended and expanded if necessary.
     *
     * @return a list of used dangerous permissions
     */
    private static List<String> dangerousPermissions() {
        List<String> dangerousPermissions = new ArrayList<>();
        dangerousPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        dangerousPermissions.add(Manifest.permission.CAMERA);
        dangerousPermissions.add(Manifest.permission.RECORD_AUDIO);
        return dangerousPermissions;
    }

    /**
     * The present list of all permissions required, including the dangerous ones. Subtract the
     * missing permissions to get a list of allowed permissions.
     *
     * @return a list of all permissions that Awesome Possum wants
     */
    public static List<String> permissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(Manifest.permission.BLUETOOTH);
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
        permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.INTERNET);
        return permissions;
    }

    /**
     * Returns a list of which permissions are NOT granted by user
     *
     * @param context a valid android context
     * @return a list of denied permissions, empty array if none
     */
    public static List<String> missingPermissions(@NonNull Context context) {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : dangerousPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    /**
     * A quick method to check if there are some permissions that are missing or needed
     *
     * @param context a valid android context
     * @return true if some permissions are missing, false if not
     */
    public boolean hasMissingPermissions(@NonNull Context context) {
        return missingPermissions(context).size() > 0;
    }

    /**
     * Stops any actual listening. Only fired if it is actually listening
     */
    public void stopListening() {
        if (status.get() != CoreStatus.Idle) {
            for (AbstractDetector detector : detectors)
                detector.terminate();
            status.set(CoreStatus.Idle);
        }
    }

    /**
     * Adds a listener for changes to detectors
     *
     * @param listener a listener for changes
     */
    @SuppressWarnings("unused")
    public void addChangeListener(IDetectorChange listener) {
        changeListeners.add(listener);
    }

    /**
     * Removes a listener for changes to detectors
     *
     * @param listener a listener for changes
     */
    @SuppressWarnings("unused")
    public void removeChangeListener(IDetectorChange listener) {
        changeListeners.remove(listener);
    }

    /**
     * Changes the timeout to a value you want to use
     *
     * @param timeOut time in milliseconds the detectors should run before terminating
     */
    public void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }

    /**
     * Quick check for whether the system is listening atm
     *
     * @return true if it is listening, false if in other state
     */
    public boolean isListening() {
        return status.get() != CoreStatus.Idle;
    }

    /**
     * Lets you change which user id you are currently handling, changing all detectors to
     * utilize the new userId. Does NOT validate in any way the new user id
     *
     * @param newUserId a new user id to utilize for gathering/authenticating
     */
    public void changeUserId(String newUserId) {
        for (AbstractDetector detector : detectors) {
            detector.setUniqueUserId(newUserId);
        }
        userId = newUserId;
    }

    /**
     * Quick way to return the current user using the sdk
     *
     * @return the user id being used
     */
    protected String userId() {
        return userId;
    }
}