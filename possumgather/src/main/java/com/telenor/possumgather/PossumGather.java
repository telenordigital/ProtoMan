package com.telenor.possumgather;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.gson.JsonArray;
import com.telenor.possumcore.PossumCore;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.CoreStatus;
import com.telenor.possumcore.detectors.Accelerometer;
import com.telenor.possumcore.detectors.AmbientSoundDetector;
import com.telenor.possumcore.detectors.BluetoothDetector;
import com.telenor.possumcore.detectors.GyroScope;
import com.telenor.possumcore.detectors.HardwareDetector;
import com.telenor.possumcore.detectors.ImageDetector;
import com.telenor.possumcore.detectors.LocationDetector;
import com.telenor.possumcore.detectors.NetworkDetector;
import com.telenor.possumgather.upload.AmazonUploadService;
import com.telenor.possumgather.utils.CountingOutputStream;
import com.telenor.possumgather.utils.GatherUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipOutputStream;

/**
 * Handles all data gather and upload to the Amazon data storage
 */
public class PossumGather extends PossumCore {
    private static final String tag = PossumGather.class.getName();
    private static final String amazonCatalogue = "data.30012018";

    /**
     * Constructor for the gather library. Creating this instance will enable you to access and
     * gather data for further upload
     *
     * @param context a valid android context
     * @param uniqueUserId the unique user id of the person you want to gather data about
     */
    public PossumGather(Context context, String uniqueUserId) {
        super(context, uniqueUserId);
        setTimeOut(300000); // Maximum 5 minutes of listening before session is ended
    }

    /**
     * Default abstraction of which detectors are to be included. Each subset of PossumCore needs
     * to implement this to add all detectors it desires to listen to. This can also be overridden
     * by sub-methods to reduce the number of detectors if so desired.
     *
     * @param context a valid android context
     */
    @Override
    protected void addAllDetectors(Context context) {
        addDetector(new HardwareDetector(context));
        addDetector(new Accelerometer(context));
        addDetector(new AmbientSoundDetector(context));
        addDetector(new GyroScope(context));
        addDetector(new NetworkDetector(context));
        addDetector(new LocationDetector(context));
        addDetector(new ImageDetector(context, "tensorflow_facerecognition.pb"));
        addDetector(new BluetoothDetector(context));
    }

    /**
     * Handles upload of data to the amazon. Must be called manually after stopListening has been
     * called. Will take all files stored locally and push to
     *
     * @param context a valid android context
     * @param identityPoolId the amazon identityPool id used for upload
     * @param bucket the bucket it should upload to on Amazon
     */
    public void upload(@NonNull Context context, @NonNull String identityPoolId, @NonNull String bucket) {
        // TODO: Copy and refactor upload code/functionality from old version
        // TODO: Check present files and confirm they exist and is not missing
        Intent intent = new Intent(context, AmazonUploadService.class);
        intent.putExtra("bucket", bucket);
        intent.putExtra("identityPoolId", identityPoolId);
        if (!isUploading(context)) {
            context.startService(intent);
        } else {
            Log.i(tag, "AP: Uploading already happening");
        }
    }

    /**
     * A handy way to get the version of the possumGather library
     *
     * @param context a valid android context
     * @return a string representing the current version of the library
     */
    public static String version(@NonNull Context context) {
        return context.getString(R.string.possum_gather_version_name);
    }


    /**
     * Stops any actual listening. Only fired if it is actually listening.
     * When fired, it will store all data registered to file after zipping it.
     */
    @Override
    public void stopListening() {
        super.stopListening();
        if (detectors().size() == 0) {
            Log.i(tag, "AP: No detectors, no file storing");
            return;
        }
        setStatus(CoreStatus.Processing);
        Context context = detectors().iterator().next().context();
        File storedCatalogue = GatherUtils.storageCatalogue(context);
        String version = version(context);

        for (AbstractDetector detector : detectors()) {
            for (String dataSet : detector.dataStored().keySet()) {
                try {
                    String setName = dataSet.equals("default")?detector.detectorName():dataSet;
                    String fileName = String.format(Locale.US, "%s#%s#%s#%s#%s.zip", amazonCatalogue, version, setName, detector.getUserId(), detector.now());
                    List<JsonArray> data = detector.dataStored().get(dataSet);
                    if (data.size() > 0) {
                        File uploadFile = new File(storedCatalogue, fileName);
                        CountingOutputStream innerStream = new CountingOutputStream(new FileOutputStream(uploadFile));
                        try {
                            ZipOutputStream outerStream = GatherUtils.createZipStream(innerStream, dataSet);
                            for (JsonArray value : data) {
                                try {
                                    if (outerStream != null) {
                                        outerStream.write(value.toString().getBytes());
                                        outerStream.write("\r\n".getBytes());
                                    }
                                } catch (Exception e) {
                                    Log.e(tag, "AP: FailedToWrite:", e);
                                }
                            }
                            if (outerStream != null) outerStream.close();
                            innerStream.close();
                        } catch (Exception e) {
                            Log.i(tag, "AP: Failed to create zipStream:",e);
                        }
                    }
                } catch (Exception e) {
                    Log.e(tag, "AP: Failed to store file:", e);
                }
            }
        }
        setStatus(CoreStatus.Idle);
    }

    /**
     * Function for determining how much data is stored as files
     *
     * @return the bytes stored in all saved datafiles
     */
    @SuppressWarnings("unused")
    public long spaceUsed(@NonNull Context context) {
        List<File> files = GatherUtils.getFiles(context);
        long size = 0;
        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    /**
     * Quick method to check if data is being uploaded
     *
     * @param context a valid android context
     * @return true if service is uploading, false if not
     */
    @SuppressWarnings("all")
    public boolean isUploading(@NonNull Context context) {
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return false;
        List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            if (runningServiceInfo.service.getClassName().equals(AmazonUploadService.class.getName())){
                return true;
            }
        }
        return false;
    }

    /**
     * Used for emergency deletion of all stored data files. Only use if absolutely necessary as it
     * will cause a loss of data. All uploaded files are automatically deleted, so try to be
     * vigilant in uploading instead.
     */
    public void deleteStored(@NonNull Context context) {
        List<File> files = GatherUtils.getFiles(context);
        for (File file : files) {
            if (!file.delete()) {
                Log.e(tag, "AP: Failed to delete file:"+file.getName());
            }
        }
    }
}