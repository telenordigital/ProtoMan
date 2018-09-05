package com.telenor.possumcore;

import android.Manifest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.FileFsFile;
import org.robolectric.res.FsFile;

import java.util.List;

@Config(constants = BuildConfig.class) //, manifest = Config.NONE
@RunWith(RobolectricTestRunner.class)
public class PermissionsTest {
    @Test
    public void testPermissionsOfCore() {
        Config config = new Config.Builder().setManifest("src/main/AndroidManifest.xml").setConstants(BuildConfig.class).build();
        String moduleRoot = getModuleRootPath(config);
        FsFile androidManifestFile = FileFsFile.from(moduleRoot, "src/main/AndroidManifest.xml");
        FsFile resDirectory = FileFsFile.from(moduleRoot, "src/main/res");
        FsFile assetsDirectory = FileFsFile.from(moduleRoot, "src/main/assets");
        AndroidManifest androidManifest = new AndroidManifest(androidManifestFile, resDirectory, assetsDirectory);
        List<String> usedPermissions = androidManifest.getUsedPermissions();
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.ACCESS_NETWORK_STATE));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.BLUETOOTH));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.BLUETOOTH_ADMIN));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.ACCESS_WIFI_STATE));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.CAMERA));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.RECORD_AUDIO));
        Assert.assertTrue(usedPermissions.contains(Manifest.permission.INTERNET));
        Assert.assertEquals(8, usedPermissions.size());
    }

    private String getModuleRootPath(Config config) {
        String moduleRoot = config.constants().
                getResource("").toString().
                replace("file:", "").
                replace("jar:", "");
        return moduleRoot.substring(0, moduleRoot.indexOf("/build"));
    }
}