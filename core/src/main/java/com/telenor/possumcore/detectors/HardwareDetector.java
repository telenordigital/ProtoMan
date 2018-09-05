package com.telenor.possumcore.detectors;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

import com.google.gson.JsonArray;
import com.telenor.possumcore.PossumCore;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.interfaces.IDetectorChange;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Retrieves your device' hardware information, like android version or model. This
 * to determine if there are correlation between specific models or versions and data accuracy
 */
public class HardwareDetector extends AbstractDetector {
    private static final String permissions = "permissions";
    public HardwareDetector(@NonNull Context context) {
        this(context, null);
    }
    public HardwareDetector(@NonNull Context context, IDetectorChange listener) {
        super(context, listener);
        createDataSet(permissions);
    }

    @Override
    public int queueLimit(@NonNull String key) {
        return 300;
    }

    @Override
    public int detectorType() {
        return DetectorType.Hardware;
    }

    @Override
    public String detectorName() {
        return "hardware";
    }

    @Override
    public void run() {
        super.run();
        JsonArray array = new JsonArray();
        array.add(""+now());
        array.add("HARDWARE_INFO START");
        array.add("Board:" + Build.BOARD);
        array.add("Brand:" + Build.BRAND);
        array.add("Device:" + Build.DEVICE);
        array.add("Display:" + Build.DISPLAY);
        array.add("Fingerprint:" + Build.FINGERPRINT);
        array.add("Hardware:" + Build.HARDWARE);
        array.add("Host:" + Build.HOST);
        array.add("Id:" + Build.ID);
        array.add("Manufacturer:" + Build.MANUFACTURER);
        array.add("Model:" + Build.MODEL);
        array.add("Product:" + Build.PRODUCT);
        array.add("Version:" + Build.VERSION.SDK_INT + " (" + Build.VERSION.CODENAME + ")");
        StringBuilder output = new StringBuilder();
        List<String> supported = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Collections.addAll(supported, Build.SUPPORTED_ABIS);
        } else {
            supported.add(Build.CPU_ABI);
            supported.add(Build.CPU_ABI2);
        }
        for (int i = 0; i < supported.size(); i++) {
            if (i > 0) {
                output.append(", ");
            }
            output.append(supported.get(i));
        }
        array.add("SupportedABIS:" + output.toString());
        // TODO: Add information about which detectors are not enabled?
        array.add("HARDWARE_INFO STOP");
        dataStored.get(defaultSet).add(array);

        JsonArray permissions = new JsonArray();
        permissions.add(""+now());
        List<String> allowedPerms = PossumCore.permissions();
        List<String> deniedPerms = PossumCore.missingPermissions(context());
        allowedPerms.removeAll(deniedPerms);
        if (deniedPerms.size() > 0) {
            permissions.add("PERMISSIONS START");
            for (String permission : allowedPerms)
                permissions.add(permission+" "+ PackageManager.PERMISSION_GRANTED);
            for (String permission : deniedPerms)
                permissions.add(permission+" " + PackageManager.PERMISSION_DENIED);
            permissions.add("PERMISSIONS END");
            dataStored.get(HardwareDetector.permissions).add(permissions);
        }
    }

    @Override
    public void terminate() {

    }
}