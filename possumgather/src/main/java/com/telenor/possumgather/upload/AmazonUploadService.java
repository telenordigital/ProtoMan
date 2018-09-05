package com.telenor.possumgather.upload;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.auth.IdentityChangedListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.telenor.possumgather.R;
import com.telenor.possumgather.utils.GatherUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles the actual upload of the files stored to Amazon cloud
 */
public class AmazonUploadService extends Service implements TransferListener, IdentityChangedListener {
    private CognitoCachingCredentialsProvider cognitoProvider;
    private SparseArray<File> filesUploaded;
    private String regionName;
    private AtomicInteger fileCounter;
    private String bucket;
    private static final String tag = AmazonUploadService.class.getName();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        List<File> files = GatherUtils.getFiles(this);
        if (files.size() == 0) {
            Log.i(tag, "AP: No files found, terminating service");
            stopSelf();
            return START_NOT_STICKY;
        }
        String identityPoolId = intent.getStringExtra("identityPoolId");
        bucket = intent.getStringExtra("bucket");
        String version = getResources().getString(R.string.possum_gather_version_name);
        Log.i(tag, "AP: Version number:" + version);
        if (identityPoolId == null || bucket == null) {
            // Sending in invalid parameters terminates service as well as any already running
            // operation
            Log.i(tag, "AP: Service missing parameters, stopping:" + identityPoolId + "," + bucket);
            stopSelf();
        } else {
            regionName = identityPoolId.split(":")[0];
            filesUploaded = new SparseArray<>();
            cognitoProvider = new CognitoCachingCredentialsProvider(
                    this,
                    identityPoolId,
                    Regions.fromName(regionName)
            );
            if (cognitoProvider.getCachedIdentityId() != null) {
                Log.i(tag, "AP: Found identity, starting");
                startUpload();
            } else {
                Log.i(tag, "AP: Did not find identity, checking for it");
                cognitoProvider.registerIdentityChangedListener(this);
                new AmazonIdentity().execute(cognitoProvider);
            }
        }
        return START_NOT_STICKY;
    }

    private static class AmazonIdentity extends AsyncTask<CognitoCachingCredentialsProvider, Void, String> {
        @Override
        protected String doInBackground(CognitoCachingCredentialsProvider... providers) {
            if (providers[0] == null) return null;
            return providers[0].getIdentityId();
        }
    }

    private void startUpload() {
        AmazonS3Client amazonS3Client = new AmazonS3Client(cognitoProvider);
        amazonS3Client.setRegion(Region.getRegion(Regions.fromName(regionName)));
        TransferUtility transferUtility = new TransferUtility(amazonS3Client, this);
        List<File> files = GatherUtils.getFiles(this);
        fileCounter = new AtomicInteger(files.size());
        for (File file : files) {
            String key = file.getName().replace("#", "/");
            if (file.exists() && file.length() > 0) {
                Log.i(tag, "AP: Uploading:" + file.getAbsolutePath());
                TransferObserver observer = transferUtility.upload(bucket, key, file);
                filesUploaded.put(observer.getId(), file);
                observer.setTransferListener(this);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onStateChanged(int id, TransferState state) {
        switch (state) {
            case FAILED:
                fileCounter.getAndDecrement();
                break;
            case COMPLETED:
                fileCounter.getAndDecrement();
                File file = filesUploaded.get(id);
                if (file.exists()) {
                    if (!file.delete()) {
                        Log.i(tag, "AP: Failed to delete file:" + file.getAbsolutePath());
                    } else {
                        Log.i(tag, "AP: Completed and deleted file:" + file.getAbsolutePath());
                    }
                }
                break;
            default:
        }
        if (fileCounter.get() == 0) {
            Log.i(tag, "AP: Finished uploading.");
            stopSelf();
        }
    }

    @Override
    public void onProgressChanged(int id, long bytesCurrent, long bytesTotal) {
    }

    @Override
    public void onError(int id, Exception ex) {
        Log.i(tag, "AP: Error:" + id + ":", ex);
    }

    @Override
    public void identityChanged(String oldIdentityId, String newIdentityId) {
        if (cognitoProvider.getCachedIdentityId() != null) {
            cognitoProvider.unregisterIdentityChangedListener(this);
            Log.i(tag, "AP: Identity check found you, starting upload");
            startUpload();
        } else {
            Log.e(tag, "AP: Unable to get identity for upload: Find out what happens");
            stopSelf();
        }
    }
}