package com.telenor.possumcore.abstractdetectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;

import com.telenor.possumcore.interfaces.IDetectorChange;

/**
 * Abstract receiver detector, capable of receiving intents from a given intentFilter. Will send
 * all intents registered to filter in an abstract onReceiveData method. Handles registering and
 * unRegistering of receiver in terminate/cleanup method
 */
public abstract class AbstractReceiverDetector extends AbstractDetector {
    private BroadcastReceiver receiver;
    private boolean isRegistered;
    private boolean isAlwaysOn;
    private IntentFilter intentFilter = new IntentFilter();

    public AbstractReceiverDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveData(intent);
            }
        };
    }

    protected void addFilterAction(String action) {
        intentFilter.addAction(action);
    }

    /**
     * Ensures the receiver is always on if so. Should be run during constructor of subclass
     */
    protected void receiverIsAlwaysOn() {
        isAlwaysOn = true;
        registerReceiver();
    }

    private void registerReceiver() {
        if (!isRegistered && intentFilter.countActions() > 0) {
            context().registerReceiver(receiver, intentFilter);
            isRegistered = true;
        }
    }

    /**
     * Registers receiver if not already done and runs.
     */
    @Override
    public void run() {
        super.run();
        registerReceiver();
    }

    @Override
    public void terminate() {
        unregisterReceiver(true);
    }

    private void unregisterReceiver(boolean checkForAlwaysOn) {
        if (isRegistered && (!checkForAlwaysOn || !isAlwaysOn)) {
            context().unregisterReceiver(receiver);
            isRegistered = false;
        }
    }

    @Override
    public void onResume(boolean continueRunning) {
        super.onResume(continueRunning);
        registerReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(true);
    }

    /**
     * Method for ensuring receiver is removed upon Possum termination. Terminate will also call
     * this, but not if it is always on. Cleanup removes it regardless.
     */
    @Override
    public void cleanUp() {
        super.cleanUp();
        unregisterReceiver(false);
    }

    /**
     * Method that is fired during receivers call
     *
     * @param intent the intent containing action and data
     */
    protected abstract void onReceiveData(Intent intent);
}