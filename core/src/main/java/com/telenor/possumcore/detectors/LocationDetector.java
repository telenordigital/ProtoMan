package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumcore.abstractdetectors.AbstractReceiverDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

/**
 * Retrieves locational information based on network or gps to pinpoint your whereabouts in
 * correspondence to your present data in order to indicate if you follow your standard
 * patterns or not
 */
public class LocationDetector extends AbstractReceiverDetector implements LocationListener {
    private LocationManager locationManager;
    private Handler locationHandler;
    private static final int minTimePositionInterval = 1000; // Least amount of time between positions
//    private static final long maxScanTime = 60*1000;

    public LocationDetector(@NonNull Context context) {
        this(context, null);
    }
    public LocationDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        addFilterAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationHandler = getHandler();
    }

    /**
     * Determines whether the location detector can be used, in effect whether the permission is
     * valid and a provider is available to peruse (only gps and network can be used)
     *
     * @return true if permission is granted and a provider is available
     */
    @Override
    public boolean isAvailable() {
        return super.isAvailable() && locationManager != null && locationManager.getProviders(true).size() > 0;
    }

    @Override
    public int queueLimit(@NonNull String key) {
        return 20; // Default set
    }

    /**
     * Confirms that the device has location capabilities and that at least one provider is
     * available (whether or not it is permitted)
     *
     * @return true if this detector is enabled
     */
    @Override
    public boolean isEnabled() {
        return locationManager != null && !locationManager.getAllProviders().isEmpty();
    }

    protected Handler getHandler() {
        return new Handler(Looper.getMainLooper());
    }

    /**
     * Ensures no updates are running, removing them if it is already being used. This can cause
     * a problem for authentication when the low timespan of auth can cause a lot of attempts to
     * fail to get a position when it needs to
     */
    @Override
    public void terminate() {
        super.terminate();
        locationHandler.removeCallbacks(this);
        locationManager.removeUpdates(this);
    }

    /**
     * The actual gathering of a location. Atm it will consistently poll for locations until it is
     * terminated. It will also stop when you exit the app (and restart when reentering - if it
     * was running when exited)
     */
    @SuppressWarnings("MissingPermission")
    @Override
    public void run() {
        super.run();
        if (isEnabled() && isAvailable()) {
//            locationHandler.postDelayed(this::terminate, maxScanTime); // Note: Max scan time to prevent overuse of battery
            onLocationChanged(lastLocation());
            for (String provider : locationManager.getProviders(true)) {
                requestProviderPositions(provider);
            }
        }
    }

    /**
     * The actual requester of position. Can be overridden for use-cases where a multiple locations
     * is not desired. Caution, this method ignores permissions as it is intended to be used in the
     * run() method where permission is confirmed before it is called. Do not call this method by
     * itself.
     *
     * @param provider the provider it should gather for, either LocationManager.GPS_PROVIDER or
     *                 LocationManager.NETWORK_PROVIDER
     */
    @SuppressWarnings("MissingPermission")
    protected void requestProviderPositions(@NonNull String provider) {
        if (locationManager != null) {
            locationManager.requestLocationUpdates(provider, minTimePositionInterval, 0, this, Looper.getMainLooper());
        }
    }

    /**
     * Finds the last recorded scanResult. Focuses on time and ignores accuracy. The best and most
     * recent time fitting location is
     *
     * @return the last known location or null
     */
    @SuppressWarnings("MissingPermission")
    private Location lastLocation() {
        Location lastLocation = null;
        if (locationManager != null && isPermitted()) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation == null) {
                lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } else {
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null && networkLocation.getTime() < lastLocation.getTime())
                    lastLocation = networkLocation;
            }
        }
        return lastLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        JsonArray array = new JsonArray();
        array.add("" + now());
//        array.add("" + location.getTime());
        // TODO: Get @alex on backend to fix format so it accepts the positions timestamp as well
        array.add("" + location.getLatitude());
        array.add("" + location.getLongitude());
        array.add("" + location.getAltitude());
        array.add("" + location.getAccuracy());
        array.add(location.getProvider());
        streamData(array);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        detectorStatusChanged();
    }

    @Override
    public void onProviderEnabled(String provider) {
        detectorStatusChanged();
    }

    @Override
    public void onProviderDisabled(String provider) {
        detectorStatusChanged();
    }

    @Override
    public int detectorType() {
        return DetectorType.Position;
    }

    @Override
    public String detectorName() {
        return "position";
    }

    @Override
    protected void onReceiveData(Intent intent) {
        detectorStatusChanged();
    }

    @Override
    public String requiredPermission() {
        return Manifest.permission.ACCESS_FINE_LOCATION;
    }
}