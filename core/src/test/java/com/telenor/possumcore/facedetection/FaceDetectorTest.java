package com.telenor.possumcore.facedetection;

import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
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

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class FaceDetectorTest {
    @Mock
    private Detector<Face> mockedDetector;
    @Mock
    private IFaceFound mockedInterface;

    private FaceDetector faceDetector;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        faceDetector = new FaceDetector(mockedDetector, mockedInterface);
    }

    @After
    public void tearDown() {
        faceDetector = null;
    }

    @Test
    public void testInitialize() throws Exception {
        Assert.assertNotNull(faceDetector);
        Field delegateField = FaceDetector.class.getDeclaredField("delegate");
        delegateField.setAccessible(true);
        Assert.assertNotNull(delegateField.get(faceDetector));
    }

    @Test
    public void testOperational() {
        when(mockedDetector.isOperational()).thenReturn(true);
        Assert.assertTrue(faceDetector.isOperational());
        when(mockedDetector.isOperational()).thenReturn(false);
        Assert.assertFalse(faceDetector.isOperational());
    }

    @Test
    public void destroyReleasesDelegate() {
        verify(mockedDetector, never()).release();
        faceDetector.destroy();
        verify(mockedDetector, times(1)).release();
        faceDetector.destroy();
        verify(mockedDetector, times(1)).release();
    }

    @Test
    public void testRelease() {
        Assert.assertFalse(faceDetector.isReleased());
        faceDetector.destroy();
        Assert.assertTrue(faceDetector.isReleased());
    }

    @Test
    public void testEmptyArrayOnNullFrame() {
        SparseArray emptyList = faceDetector.detect(null);
        Assert.assertTrue(emptyList.size() == 0);
        faceDetector = new FaceDetector(null, mockedInterface);
        emptyList = faceDetector.detect(mock(Frame.class));
        Assert.assertTrue(emptyList.size() == 0);
    }

    @Test
    public void testDetectFrame() {
        Frame mockedFrame = mock(Frame.class);
        SparseArray<Face> fakeFaces = new SparseArray<>();
        Face mockedFace = mock(Face.class);
        fakeFaces.append(1, mockedFace);
        when(mockedDetector.detect(any(Frame.class))).thenReturn(fakeFaces);
        verify(mockedDetector, never()).detect(any(Frame.class));
        verify(mockedInterface, never()).faceFound(any(Face.class), any(Frame.class));
        SparseArray<Face> facesFound = faceDetector.detect(mockedFrame);
        verify(mockedDetector, times(1)).detect(any(Frame.class));
        verify(mockedInterface, times(1)).faceFound(any(Face.class), any(Frame.class));
        Assert.assertNotNull(facesFound);
        Assert.assertEquals(1, facesFound.size());
        Assert.assertSame(mockedFace, facesFound.get(1));
    }
    @Test
    public void testDetectFrameWithNoFacesDoesNothing() {
        Frame mockedFrame = mock(Frame.class);
        SparseArray<Face> fakeFaces = new SparseArray<>();
        when(mockedDetector.detect(any(Frame.class))).thenReturn(fakeFaces);
        SparseArray<Face> facesFound = faceDetector.detect(mockedFrame);
        Assert.assertEquals(0, facesFound.size());
        verify(mockedInterface, never()).faceFound(any(Face.class), any(Frame.class));
    }
}