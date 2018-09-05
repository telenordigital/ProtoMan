package com.telenor.possumcore;

import android.Manifest;
import android.app.Activity;
import android.content.Context;

import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.CoreStatus;
import com.telenor.possumcore.detectors.Accelerometer;
import com.telenor.possumcore.detectors.ImageDetector;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class PossumCoreTest {
    private PossumCore possumCore;
    @Mock
    private Accelerometer mockedAccelerometer;
    @Mock
    private ImageDetector mockedImageDetector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        possumCore = new PossumCore(RuntimeEnvironment.application, "testId") {
            @Override
            protected void addAllDetectors(Context context) {
                addDetector(mockedAccelerometer);
                addDetector(mockedImageDetector);
            }
        };
    }

    @After
    public void tearDown() {
        possumCore = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(possumCore);
    }

    @Test
    public void testStartListeningWithDetectors() throws Exception {
        Field detectorsField = PossumCore.class.getDeclaredField("detectors");
        detectorsField.setAccessible(true);
        HashSet<AbstractDetector> detectors = (HashSet<AbstractDetector>) detectorsField.get(possumCore);
        Assert.assertTrue(detectors.size() == 2);
        verify(mockedAccelerometer, Mockito.times(0)).run();
        Assert.assertTrue(possumCore.startListening());
        Field executorField = PossumCore.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService service = (ExecutorService) executorField.get(possumCore);
        service.awaitTermination(1, TimeUnit.SECONDS);
        verify(mockedAccelerometer, Mockito.times(1)).run();
    }

    @Test
    public void testPermissions() {
        List<String> permissions = PossumCore.permissions();
        Assert.assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_ADMIN));
        Assert.assertTrue(permissions.contains(Manifest.permission.BLUETOOTH));
        Assert.assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        Assert.assertTrue(permissions.contains(Manifest.permission.ACCESS_NETWORK_STATE));
        Assert.assertTrue(permissions.contains(Manifest.permission.ACCESS_WIFI_STATE));
        Assert.assertTrue(permissions.contains(Manifest.permission.CAMERA));
        Assert.assertTrue(permissions.contains(Manifest.permission.RECORD_AUDIO));
        Assert.assertTrue(permissions.contains(Manifest.permission.INTERNET));
        Assert.assertEquals(8, permissions.size());
    }

    @Test
    public void testGetAndSetStatus() throws Exception {
        Field statusField = PossumCore.class.getDeclaredField("status");
        statusField.setAccessible(true);
        Assert.assertTrue(statusField.get(possumCore) instanceof AtomicInteger);
        AtomicInteger status = (AtomicInteger)statusField.get(possumCore);
        Assert.assertEquals(CoreStatus.Idle, status.get());
        possumCore.setStatus(CoreStatus.Running);
        status = (AtomicInteger)statusField.get(possumCore);
        Assert.assertEquals(CoreStatus.Running, status.get());
        possumCore.setStatus(CoreStatus.Processing);
        Assert.assertEquals(CoreStatus.Processing, possumCore.getStatus());
    }

    @Test
    public void testMissingPermissions() throws Exception {
        Assert.assertTrue(possumCore.hasMissingPermissions(RuntimeEnvironment.application));
        Method missingPermissions = PossumCore.class.getDeclaredMethod("missingPermissions", Context.class);
        missingPermissions.setAccessible(true);
        List<String> missingPerms = (List<String>)missingPermissions.invoke(possumCore, RuntimeEnvironment.application);
        Assert.assertEquals(3, missingPerms.size());
        Assert.assertTrue(missingPerms.contains(Manifest.permission.CAMERA));
        Assert.assertTrue(missingPerms.contains(Manifest.permission.ACCESS_FINE_LOCATION));
        Assert.assertTrue(missingPerms.contains(Manifest.permission.RECORD_AUDIO));
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);
        missingPerms = (List<String>)missingPermissions.invoke(possumCore, RuntimeEnvironment.application);
        Assert.assertEquals(2, missingPerms.size());
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION);
        missingPerms = (List<String>)missingPermissions.invoke(possumCore, RuntimeEnvironment.application);
        Assert.assertEquals(0, missingPerms.size());
        Assert.assertFalse(possumCore.hasMissingPermissions(RuntimeEnvironment.application));
    }

    @Test
    public void testRequestPermissions() {
        Activity activity = Robolectric.buildActivity(Activity.class).create().resume().get();
        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        possumCore.requestNeededPermissions(activity);
        shadowActivity.grantPermissions(Manifest.permission.CAMERA);
    }

    @Test
    public void testOnResume() {
        // TODO: Implement
        possumCore.setStatus(CoreStatus.Running);
        possumCore.onResume();
    }

    @Test
    public void testOnPause() {
        // TODO: Implement
        possumCore.setStatus(CoreStatus.Running);
        possumCore.onPause();
    }

    @Test
    public void testConfigurationChanged() {
        // TODO: Implement
        possumCore.onConfigurationChanged(null);
    }

    @Test
    public void testDetectorsMethod() throws Exception {
        Assert.assertEquals(2, possumCore.detectors().size());
        Set<AbstractDetector> detectors = possumCore.detectors();
        Field detectorsField = PossumCore.class.getDeclaredField("detectors");
        detectorsField.setAccessible(true);
        Assert.assertSame(detectors, detectorsField.get(possumCore));
    }

    @Test
    public void testStartListeningWithoutDetectors() {
        possumCore = new PossumCore(RuntimeEnvironment.application, "testId") {
            @Override
            protected void addAllDetectors(Context context) {

            }
        };
        Assert.assertFalse(possumCore.startListening());
    }

    @Test
    public void testMissingPermissionsMethod() {
        // TODO: Test Static missingPermissions method
    }

    @Test
    public void testStartListeningWhileAlreadyListening() {
        Assert.assertTrue(possumCore.startListening());
        Assert.assertFalse(possumCore.startListening());
    }

    @Test
    public void testDenyCameraBeforeListening() throws Exception {
        // TODO: Implement method
        Field camDeniedField = PossumCore.class.getDeclaredField("deniedCamera");
        camDeniedField.setAccessible(true);
        AtomicBoolean camDenied = (AtomicBoolean)camDeniedField.get(possumCore);
        Assert.assertFalse(camDenied.get());
        possumCore.denyCamera();
        camDenied = (AtomicBoolean)camDeniedField.get(possumCore);
        Assert.assertTrue(camDenied.get());

        Field executorField = PossumCore.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService mockedExecutorService = mock(ExecutorService.class);
        executorField.set(possumCore, mockedExecutorService);
        possumCore.startListening();
        verify(mockedExecutorService, never()).submit(any(ImageDetector.class));
    }

    @Test
    public void testAllowCameraBeforeListening() throws Exception {
        // TODO: Implement method
        Field camDeniedField = PossumCore.class.getDeclaredField("deniedCamera");
        camDeniedField.setAccessible(true);
        possumCore.denyCamera();
        AtomicBoolean camDenied = (AtomicBoolean)camDeniedField.get(possumCore);
        Assert.assertTrue(camDenied.get());
        possumCore.allowCamera();
        camDenied = (AtomicBoolean)camDeniedField.get(possumCore);
        Assert.assertFalse(camDenied.get());

        Field executorField = PossumCore.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService mockedExecutorService = mock(ExecutorService.class);
        executorField.set(possumCore, mockedExecutorService);
        possumCore.startListening();
        verify(mockedExecutorService, times(1)).submit(any(ImageDetector.class));
    }

    @Test
    public void testDenyCameraWhileListening() throws Exception {
        Field detectorsField = PossumCore.class.getDeclaredField("detectors");
        detectorsField.setAccessible(true);
        Set<AbstractDetector> detectors = (Set<AbstractDetector>)detectorsField.get(possumCore);
        Assert.assertEquals(2, detectors.size());
        verify(mockedImageDetector, never()).run();
        Field camDeniedField = PossumCore.class.getDeclaredField("deniedCamera");
        camDeniedField.setAccessible(true);
        Field executorField = PossumCore.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService mockedExecutorService = mock(ExecutorService.class);
        executorField.set(possumCore, mockedExecutorService);
        verify(mockedExecutorService, never()).submit(eq(mockedImageDetector));
        Assert.assertTrue(possumCore.startListening());
        verify(mockedExecutorService, times(1)).submit(eq(mockedImageDetector));
        verify(mockedImageDetector, never()).terminate();
        possumCore.denyCamera();
        verify(mockedImageDetector, times(1)).terminate();
    }

    @Test
    public void testAllowCameraWhileListening() throws Exception {
        Field executorField = PossumCore.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        ExecutorService mockedExecutorService = mock(ExecutorService.class);
        executorField.set(possumCore, mockedExecutorService);
        verify(mockedExecutorService, never()).submit(eq(mockedImageDetector));
        Assert.assertTrue(possumCore.startListening());
        verify(mockedExecutorService, times(1)).submit(eq(mockedImageDetector));
        possumCore.denyCamera();
        verify(mockedImageDetector, times(1)).terminate();
        possumCore.allowCamera();
        verify(mockedExecutorService, times(2)).submit(eq(mockedImageDetector));
    }

    @Test
    public void testStopListening() throws Exception {
        possumCore.startListening();
        Field statusField = PossumCore.class.getDeclaredField("status");
        statusField.setAccessible(true);
        AtomicInteger status = (AtomicInteger) statusField.get(possumCore);
        Assert.assertEquals(CoreStatus.Running, status.get());
        possumCore.stopListening();
        status = (AtomicInteger) statusField.get(possumCore);
        Assert.assertEquals(CoreStatus.Idle, status.get());
    }

    @Test
    public void testIsRunning() throws Exception {
        Assert.assertEquals(CoreStatus.Idle, possumCore.getStatus());
        Field listenField = PossumCore.class.getDeclaredField("status");
        listenField.setAccessible(true);
        AtomicInteger listen = (AtomicInteger) listenField.get(possumCore);
        listen.set(CoreStatus.Running);
        Assert.assertEquals(CoreStatus.Running, possumCore.getStatus());
    }

    @Test
    public void testSetTimeOut() throws Exception {
        Field timeOutField = PossumCore.class.getDeclaredField("timeOut");
        timeOutField.setAccessible(true);
        long timeout = (long) timeOutField.get(possumCore);
        Assert.assertEquals(3000, timeout);
        possumCore.setTimeOut(100);
        timeout = (long) timeOutField.get(possumCore);
        Assert.assertEquals(100, timeout);
    }
}