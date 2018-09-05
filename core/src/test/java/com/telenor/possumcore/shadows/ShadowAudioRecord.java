package com.telenor.possumcore.shadows;

import android.media.AudioRecord;
import android.support.annotation.NonNull;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Shadow for {@link android.media.AudioRecord}.
 */
@SuppressWarnings({"UnusedDeclaration"})
@Implements(AudioRecord.class)
public class ShadowAudioRecord {
    private static int minBufferSize = 100;
    public int audioSource;
    public int sampleRate;
    public int channelConfig;
    public int audioFormat;
    public int bufferSize;
    public boolean isRecording = false;
    private static final String tag = ShadowAudioRecord.class.getName();

    public ShadowAudioRecord() {

    }

    public ShadowAudioRecord(int audioSource, int sampleRate, int channelConfig, int audioFormat, int bufferSize) {
        this.audioSource = audioSource;
        this.sampleRate = sampleRate;
        this.channelConfig = channelConfig;
        this.audioFormat = audioFormat;
        this.bufferSize = bufferSize;
    }

    @Implementation
    public int read(@NonNull short[] buffer, int offsetInShorts, int sizeInShorts) {
        for (int count = offsetInShorts; count < sizeInShorts; count++) {
            buffer[count] = (short) 1;
        }
        return sizeInShorts;
    }

    @Implementation
    public void startRecording() throws IllegalStateException {
        isRecording = true;
    }

    @Implementation
    public void stop() throws IllegalStateException {
        isRecording = false;
    }

    @Implementation
    static public int getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat) {
        return minBufferSize;
    }

    @Implementation
    public int getRecordingState() {
        return isRecording ? AudioRecord.RECORDSTATE_RECORDING : AudioRecord.RECORDSTATE_STOPPED;
    }

    public void setMinBufferSize(final int minBufferSize) {
        ShadowAudioRecord.minBufferSize = minBufferSize;
    }
}