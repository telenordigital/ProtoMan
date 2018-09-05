package com.telenor.possumcore.facedetection;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.telenor.possumcore.BuildConfig;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class FaceProcessorTest {
    @Mock
    private Detector<Face> mockedDetector;
    @Mock
    private Tracker<Face> mockedTracker;

    private FaceProcessor faceProcessor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        faceProcessor = new FaceProcessor(mockedDetector, mockedTracker);
    }

    @After
    public void tearDown() {
        faceProcessor = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(faceProcessor);
    }
    @Test
    public void testSelectFocusReturnsZero() {
        Assert.assertEquals(0, faceProcessor.selectFocus(null));
    }
}