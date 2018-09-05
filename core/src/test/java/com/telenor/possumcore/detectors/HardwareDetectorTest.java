package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.DetectorType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class HardwareDetectorTest {
    private HardwareDetector hardwareDetector;

    @Before
    public void setUp() {
        hardwareDetector = new HardwareDetector(RuntimeEnvironment.application);
    }

    @After
    public void tearDown() {
        hardwareDetector = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(hardwareDetector);
        Assert.assertEquals(DetectorType.Hardware, hardwareDetector.detectorType());
        Assert.assertEquals("hardware", hardwareDetector.detectorName());
    }

    @Test
    public void testRun() throws Exception {
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> datas = (Map<String, List<JsonArray>>)dataStoredField.get(hardwareDetector);
        Assert.assertEquals(2, datas.keySet().size());
        Assert.assertEquals(0 ,datas.get("default").size());
        hardwareDetector.run();
        datas = (Map<String, List<JsonArray>>)dataStoredField.get(hardwareDetector);
        List<JsonArray> dataStored = datas.get("default");
        Assert.assertEquals(1, dataStored.size());
        JsonArray data = dataStored.get(0);
        Assert.assertEquals(16, data.size());
        Assert.assertEquals("HARDWARE_INFO START", data.get(1).getAsString());
        Assert.assertEquals("HARDWARE_INFO STOP", data.get(15).getAsString());
    }

    @Config(sdk = Build.VERSION_CODES.KITKAT)
    @Test
    public void testRunOnBeforeLollipop() throws Exception {
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        hardwareDetector.run();
        Map<String, List<JsonArray>> datas = (Map<String, List<JsonArray>>)dataStoredField.get(hardwareDetector);
        List<JsonArray> dataStored = datas.get("default");
        Assert.assertEquals(1, dataStored.size());
        JsonArray data = dataStored.get(0);
        Assert.assertEquals(16, data.size());
    }

    @Test
    public void testPermissionDataSetExists() throws Exception {
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> datas = (Map<String, List<JsonArray>>)dataStoredField.get(hardwareDetector);
        Assert.assertNotNull(datas.get("permissions"));
    }

    @Test
    public void testPermissionsAreCorrectlyCalled() throws Exception {
        ShadowApplication.getInstance().denyPermissions(Manifest.permission.CAMERA);
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        hardwareDetector.run();
        Map<String, List<JsonArray>> datas = (Map<String, List<JsonArray>>)dataStoredField.get(hardwareDetector);
        Assert.assertEquals(1, datas.get("permissions").size());
        JsonArray data = datas.get("permissions").get(0);
        Assert.assertEquals("PERMISSIONS START", data.get(1).getAsString());
        List<String> perms = new ArrayList<>();
        for (JsonElement el : data)
            perms.add(el.getAsString());
        Assert.assertTrue(perms.contains(String.format(Locale.US, "%s %s", Manifest.permission.CAMERA, PackageManager.PERMISSION_DENIED)));
        Assert.assertTrue(perms.contains(String.format(Locale.US, "%s %s", Manifest.permission.ACCESS_FINE_LOCATION, PackageManager.PERMISSION_GRANTED)));
        Assert.assertTrue(perms.contains(String.format(Locale.US, "%s %s", Manifest.permission.BLUETOOTH, PackageManager.PERMISSION_GRANTED)));
        Assert.assertEquals("PERMISSIONS END", data.get(data.size()-1).getAsString());
    }

    @Test
    public void testTerminate() {
        hardwareDetector.terminate();
    }
}