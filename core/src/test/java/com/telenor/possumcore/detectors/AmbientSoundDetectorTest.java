package com.telenor.possumcore.detectors;

import android.Manifest;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.os.Handler;

import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.shadows.ShadowAudioRecord;
import com.telenor.possumcore.shadows.ShadowAudioTrack;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowAudioManager;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class, shadows = {ShadowAudioTrack.class, ShadowAudioRecord.class})
@RunWith(RobolectricTestRunner.class)
public class AmbientSoundDetectorTest {
    private AmbientSoundDetector ambientSoundDetector;
    private ShadowAudioManager shadowAudioManager;

    @Before
    public void setUp() throws Exception {
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.RECORD_AUDIO);
        ambientSoundDetector = new AmbientSoundDetector(RuntimeEnvironment.application);
        Field audioManagerField = AmbientSoundDetector.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        shadowAudioManager = Shadows.shadowOf((AudioManager)audioManagerField.get(ambientSoundDetector));
        shadowAudioManager.setMicrophoneMute(false);
    }

    @After
    public void tearDown() {
        ambientSoundDetector = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(ambientSoundDetector);
        Assert.assertEquals(DetectorType.Audio, ambientSoundDetector.detectorType());
        Assert.assertEquals("sound", ambientSoundDetector.detectorName());
        Assert.assertEquals(Manifest.permission.RECORD_AUDIO, ambientSoundDetector.requiredPermission());
    }

    @Test
    public void testEnabled() throws Exception {
        Assert.assertTrue(ambientSoundDetector.isEnabled());
        Field audioManagerField = AmbientSoundDetector.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        audioManagerField.set(ambientSoundDetector, null);
        Assert.assertFalse(ambientSoundDetector.isEnabled());
    }

    @Test
    public void testAvailable() {
        Assert.assertTrue(ambientSoundDetector.isAvailable());
        ShadowApplication.getInstance().denyPermissions(Manifest.permission.RECORD_AUDIO);
        Assert.assertFalse(ambientSoundDetector.isAvailable());
    }

    @Test
    public void testRunWhenMuted() throws Exception {
        shadowAudioManager.setMicrophoneMute(true);
        Assert.assertTrue(shadowAudioManager.isMicrophoneMute());
        Field muteDisabledField = AmbientSoundDetector.class.getDeclaredField("disabledMute");
        muteDisabledField.setAccessible(true);
        Assert.assertFalse(muteDisabledField.getBoolean(ambientSoundDetector));
        ambientSoundDetector.run();
        Assert.assertFalse(shadowAudioManager.isMicrophoneMute());
        Assert.assertTrue(muteDisabledField.getBoolean(ambientSoundDetector));
    }

    @Test
    public void testRunWhenNotMuted() throws Exception {
        Field audioRecordField = AmbientSoundDetector.class.getDeclaredField("audioRecorder");
        audioRecordField.setAccessible(true);
        AudioRecord fakeRecorder = mock(AudioRecord.class);
        when(fakeRecorder.getRecordingState()).thenReturn(AudioRecord.RECORDSTATE_STOPPED);
        when(fakeRecorder.getState()).thenReturn(AudioRecord.STATE_INITIALIZED);
        audioRecordField.set(ambientSoundDetector, fakeRecorder);
        Field audioHandlerField = AmbientSoundDetector.class.getDeclaredField("audioHandler");
        audioHandlerField.setAccessible(true);
        Handler fakeHandler = mock(Handler.class);
        audioHandlerField.set(ambientSoundDetector, fakeHandler);
        ambientSoundDetector.run();
        verify(fakeRecorder, times(1)).startRecording();
        Field maxListeningField = AmbientSoundDetector.class.getDeclaredField("maxListeningTime");
        maxListeningField.setAccessible(true);
        long maxListening = maxListeningField.getLong(ambientSoundDetector);
        verify(fakeHandler, times(1)).postDelayed(any(Runnable.class), eq(maxListening));
    }

    @Test
    public void testRunWhenMissingAudioRecorder() throws Exception {
        Field audioRecordField = AmbientSoundDetector.class.getDeclaredField("audioRecorder");
        audioRecordField.setAccessible(true);
        audioRecordField.set(ambientSoundDetector, null);
        ambientSoundDetector.run();
    }

    @Test
    public void testRunWhenRecording() throws Exception {
        Assert.assertTrue(ambientSoundDetector.isEnabled());
        Assert.assertTrue(ambientSoundDetector.isAvailable());
        AudioRecord fakeRecord = mock(AudioRecord.class);
        Field audioRecordField = AmbientSoundDetector.class.getDeclaredField("audioRecorder");
        audioRecordField.setAccessible(true);
        audioRecordField.set(ambientSoundDetector, fakeRecord);
        when(fakeRecord.getRecordingState()).thenReturn(AudioRecord.RECORDSTATE_RECORDING);
        ambientSoundDetector.run();
        verify(fakeRecord, never()).startRecording();
    }

    @Test
    public void testRunWhenNotAvailable() throws Exception {
        ShadowApplication.getInstance().denyPermissions(Manifest.permission.RECORD_AUDIO);
        Assert.assertTrue(ambientSoundDetector.isEnabled());
        AudioRecord fakeRecord = mock(AudioRecord.class);
        Field audioRecordField = AmbientSoundDetector.class.getDeclaredField("audioRecorder");
        audioRecordField.setAccessible(true);
        audioRecordField.set(ambientSoundDetector, fakeRecord);
        when(fakeRecord.getRecordingState()).thenReturn(AudioRecord.RECORDSTATE_STOPPED);
        ambientSoundDetector.run();
        verify(fakeRecord, never()).startRecording();
    }

    @Test
    public void testRunWhenNotEnabled() throws Exception {
        Assert.assertTrue(ambientSoundDetector.isEnabled());
        AudioRecord fakeRecord = mock(AudioRecord.class);
        Field audioRecordField = AmbientSoundDetector.class.getDeclaredField("audioRecorder");
        audioRecordField.setAccessible(true);
        audioRecordField.set(ambientSoundDetector, fakeRecord);
        when(fakeRecord.getRecordingState()).thenReturn(AudioRecord.RECORDSTATE_STOPPED);
        Field audioManagerField = AmbientSoundDetector.class.getDeclaredField("audioManager");
        audioManagerField.setAccessible(true);
        audioManagerField.set(ambientSoundDetector, null);
        Assert.assertFalse(ambientSoundDetector.isEnabled());
        ambientSoundDetector.run();
        verify(fakeRecord, never()).startRecording();
    }

    @Test
    public void testTerminateWhenMuteDisabled() throws Exception {
        Field audioHandlerField = AmbientSoundDetector.class.getDeclaredField("audioHandler");
        audioHandlerField.setAccessible(true);
        Handler fakeHandler = Mockito.mock(Handler.class);
        audioHandlerField.set(ambientSoundDetector, fakeHandler);
        shadowAudioManager.setMicrophoneMute(false);
        Field disabledMuteField = AmbientSoundDetector.class.getDeclaredField("disabledMute");
        disabledMuteField.setAccessible(true);
        disabledMuteField.set(ambientSoundDetector, true);
        Assert.assertFalse(shadowAudioManager.isMicrophoneMute());
        verify(fakeHandler, times(0)).removeCallbacks(any(Runnable.class));
        ambientSoundDetector.terminate();
        verify(fakeHandler, times(1)).removeCallbacks(any(Runnable.class));
        Assert.assertFalse(disabledMuteField.getBoolean(ambientSoundDetector));
        Assert.assertTrue(shadowAudioManager.isMicrophoneMute());
    }

    @Test
    public void testTerminateWhenMuteNotDisabled() throws Exception {
        Field audioHandlerField = AmbientSoundDetector.class.getDeclaredField("audioHandler");
        audioHandlerField.setAccessible(true);
        Handler fakeHandler = Mockito.mock(Handler.class);
        audioHandlerField.set(ambientSoundDetector, fakeHandler);
        verify(fakeHandler, times(0)).removeCallbacks(any(Runnable.class));
        shadowAudioManager.setMicrophoneMute(false);
        Assert.assertFalse(shadowAudioManager.isMicrophoneMute());
        ambientSoundDetector.terminate();
        Assert.assertFalse(shadowAudioManager.isMicrophoneMute());
        verify(fakeHandler, times(1)).removeCallbacks(any(Runnable.class));
    }
}