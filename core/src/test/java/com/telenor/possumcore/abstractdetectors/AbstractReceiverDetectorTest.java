package com.telenor.possumcore.abstractdetectors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;

import com.telenor.possumcore.interfaces.IDetectorChange;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class AbstractReceiverDetectorTest {
    private AbstractReceiverDetector abstractReceiverDetector;
    private int counter;
    private IDetectorChange detectorChange;
    private static final String testFilterAction = "TestFilter";
    @Mock
    private Context mockedContext;
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mockedContext.getApplicationContext()).thenReturn(mockedContext);

        detectorChange = mock(IDetectorChange.class);
        counter = 0;
        abstractReceiverDetector = new AbstractReceiverDetector(RuntimeEnvironment.application, detectorChange) {
            @Override
            protected void onReceiveData(Intent intent) {
                counter++;
            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        abstractReceiverDetector.addFilterAction(testFilterAction);
    }

    @After
    public void tearDown() {
        abstractReceiverDetector = null;
    }

    @Test
    public void testInitialize() throws Exception {
        Assert.assertNotNull(abstractReceiverDetector);
        Field receiver = AbstractReceiverDetector.class.getDeclaredField("receiver");
        receiver.setAccessible(true);
        Assert.assertNotNull(receiver.get(abstractReceiverDetector));
        Field intentFilter = AbstractReceiverDetector.class.getDeclaredField("intentFilter");
        intentFilter.setAccessible(true);
        IntentFilter filter = (IntentFilter)intentFilter.get(abstractReceiverDetector);
        Assert.assertEquals("TestFilter", filter.getAction(0));
    }

    @Test
    public void testRunRegistersReceiver() {
        abstractReceiverDetector = new AbstractReceiverDetector(mockedContext, detectorChange) {
            @Override
            protected void onReceiveData(Intent intent) {
                counter++;
            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        abstractReceiverDetector.addFilterAction(testFilterAction);
        verify(mockedContext, times(0)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        abstractReceiverDetector.run();
        verify(mockedContext, times(1)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        abstractReceiverDetector.run();
        verify(mockedContext, times(1)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
    }

    @Test
    public void testTerminateRemovesReceiver() {
        abstractReceiverDetector = new AbstractReceiverDetector(mockedContext, detectorChange) {
            @Override
            protected void onReceiveData(Intent intent) {
                counter++;
            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 999;
            }

            @Override
            public String detectorName() {
                return "test";
            }
        };
        abstractReceiverDetector.addFilterAction(testFilterAction);
        verify(mockedContext, times(0)).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.terminate();
        verify(mockedContext, times(0)).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.run();
        abstractReceiverDetector.terminate();
        verify(mockedContext, times(1)).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.terminate();
        verify(mockedContext, times(1)).unregisterReceiver(any(BroadcastReceiver.class));
    }

    @Test
    public void testSendIntentIsRegistered() {
        Assert.assertEquals(0, counter);
        RuntimeEnvironment.application.sendBroadcast(new Intent("TestFilter"));
        Assert.assertEquals(0, counter);
        abstractReceiverDetector.run();
        RuntimeEnvironment.application.sendBroadcast(new Intent("TestFilter"));
        Assert.assertEquals(1, counter);
        RuntimeEnvironment.application.sendBroadcast(new Intent("TestFilter"));
        Assert.assertEquals(2, counter);
        RuntimeEnvironment.application.sendBroadcast(new Intent("TestFilter-NotReceived"));
        Assert.assertEquals(2, counter);
        abstractReceiverDetector.terminate();
        RuntimeEnvironment.application.sendBroadcast(new Intent("TestFilter"));
        Assert.assertEquals(2, counter);
    }

    @Test
    public void testCleanupUnregistersReceiver() {
        abstractReceiverDetector = new AbstractReceiverDetector(mockedContext, detectorChange) {
            @Override
            protected void onReceiveData(Intent intent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 0;
            }

            @Override
            public String detectorName() {
                return null;
            }
        };
        abstractReceiverDetector.addFilterAction(testFilterAction);
        verify(mockedContext, never()).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.cleanUp();
        verify(mockedContext, times(0)).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.receiverIsAlwaysOn();
        abstractReceiverDetector.cleanUp();
        verify(mockedContext, times(1)).unregisterReceiver(any(BroadcastReceiver.class));
        abstractReceiverDetector.cleanUp();
        verify(mockedContext, times(1)).unregisterReceiver(any(BroadcastReceiver.class));
    }

    @Test
    public void testAddFilterAction() {
        abstractReceiverDetector.addFilterAction("validMeh");
        Assert.assertEquals(0, counter);
        abstractReceiverDetector.receiverIsAlwaysOn();
        RuntimeEnvironment.application.sendBroadcast(new Intent("validMeh"));
        Assert.assertEquals(1, counter);
        RuntimeEnvironment.application.sendBroadcast(new Intent("invalidMeh"));
        Assert.assertEquals(1, counter);
    }

    @Test
    public void testAlwaysOnIsOffByDefault() throws Exception {
        Field alwaysField = AbstractReceiverDetector.class.getDeclaredField("isAlwaysOn");
        alwaysField.setAccessible(true);
        Assert.assertFalse((Boolean)alwaysField.get(abstractReceiverDetector));
    }

    @Test
    public void testDetectorChanges() {
        verify(detectorChange, times(0)).detectorChanged(any(AbstractReceiverDetector.class));
        abstractReceiverDetector.detectorStatusChanged();
        verify(detectorChange, times(1)).detectorChanged(any(AbstractReceiverDetector.class));
    }

    @Test
    public void testRegistersReceiverWhenTurnedAlwaysOn() {
        abstractReceiverDetector = new AbstractReceiverDetector(mockedContext, detectorChange) {
            @Override
            protected void onReceiveData(Intent intent) {

            }

            @Override
            public int queueLimit(@NonNull String key) {
                return 20;
            }

            @Override
            public int detectorType() {
                return 0;
            }

            @Override
            public String detectorName() {
                return null;
            }
        };
        abstractReceiverDetector.addFilterAction(testFilterAction);
        verify(mockedContext, never()).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        abstractReceiverDetector.receiverIsAlwaysOn();
        verify(mockedContext, times(1)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
        abstractReceiverDetector.receiverIsAlwaysOn();
        verify(mockedContext, times(1)).registerReceiver(any(BroadcastReceiver.class), any(IntentFilter.class));
    }
}