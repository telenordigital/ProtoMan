package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.hardware.Camera;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;
import com.google.gson.JsonArray;
import com.telenor.possumcore.BuildConfig;
import com.telenor.possumcore.TestUtils;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.facedetection.FaceDetector;
import com.telenor.possumcore.neuralnetworks.TensorWeights;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowCamera;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Config(constants = BuildConfig.class, shadows = {ShadowCamera.class}) //, abiSplit = "aarch64"
@RunWith(RobolectricTestRunner.class)
public class ImageDetectorTest {
    @Mock
    private Context mockedContext;
    @Mock
    private TensorWeights mockedTensor;
    @Mock
    private CameraSource mockedCameraSource;

    private ImageDetector imageDetector;
    private int counter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestUtils.initializeJodaTime();
        counter = 0;
        ShadowCamera.addCameraInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, getCamInfo(Camera.CameraInfo.CAMERA_FACING_FRONT, 90, true));
        ShadowCamera.addCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, getCamInfo(Camera.CameraInfo.CAMERA_FACING_BACK, 90, true));
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager());
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_CAMERA_FRONT, true);
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_CAMERA, true);
        ShadowApplication.getInstance().grantPermissions(Manifest.permission.CAMERA);

//        Assert.fail("Architecture:"+System.getProperty("os.arch"));
        when(mockedContext.checkPermission(eq(Manifest.permission.CAMERA), anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        imageDetector = new ImageDetector(RuntimeEnvironment.application, "tensorflow_facerecognition.pb") {
            @Override
            protected TensorWeights createTensor(AssetManager assetManager, String modelName) {
                return mockedTensor;
            }
        };
    }

    @After
    public void tearDown() {
        imageDetector = null;
        ShadowCamera.clearCameraInfo();
    }

    @SuppressWarnings("all")
    private Camera.CameraInfo getCamInfo(int facing, int orientation, boolean disableShutterSound) {
        Camera.CameraInfo frontCamInfo = new Camera.CameraInfo();
        frontCamInfo.facing = facing;
        frontCamInfo.orientation = orientation;
        frontCamInfo.canDisableShutterSound = disableShutterSound;
        return frontCamInfo;
    }

    @Test
    public void testInitialize() throws Exception {
        Assert.assertNotNull(imageDetector);
        Assert.assertEquals(1, counter); // Confirms openCV is initialized
        Assert.assertEquals("image", imageDetector.detectorName());
        Assert.assertEquals(DetectorType.Image, imageDetector.detectorType());
        Assert.assertEquals(Manifest.permission.CAMERA, imageDetector.requiredPermission());
        Field cameraSourceField = ImageDetector.class.getDeclaredField("cameraSource");
        cameraSourceField.setAccessible(true);
        Assert.assertNotNull(cameraSourceField.get(imageDetector));
    }

    @Test
    public void testExistenceOfDataSets() throws Exception {
        Field dataStoredField = AbstractDetector.class.getDeclaredField("dataStored");
        dataStoredField.setAccessible(true);
        Map<String, List<JsonArray>> dataStored = (Map<String, List<JsonArray>>)dataStoredField.get(imageDetector);
        Assert.assertEquals(2, dataStored.keySet().size());
        Set<String> sets = dataStored.keySet();
        Assert.assertTrue(sets.contains("default"));
        Assert.assertTrue(sets.contains("image_lbp"));
    }

    @Test
    public void testAvailable() {
        Assert.assertTrue(imageDetector.isAvailable());
        ShadowApplication.getInstance().denyPermissions(Manifest.permission.CAMERA);
        Assert.assertFalse(imageDetector.isAvailable());
    }

    @Test
    public void testEnabled() throws Exception {
        ShadowPackageManager shadowPackageManager = Shadows.shadowOf(RuntimeEnvironment.application.getPackageManager());
        Assert.assertTrue(imageDetector.isEnabled());
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_CAMERA_FRONT, false);
        Assert.assertFalse(imageDetector.isEnabled());
        shadowPackageManager.setSystemFeature(PackageManager.FEATURE_CAMERA_FRONT, true);
        Assert.assertTrue(imageDetector.isEnabled());
        Field tensorFlowField = ImageDetector.class.getDeclaredField("tensorFlowInterface");
        tensorFlowField.setAccessible(true);
        tensorFlowField.set(imageDetector, null);
        Assert.assertFalse(imageDetector.isEnabled());
    }

    @Test
    public void testRunFiresCameraSourceStart() throws Exception {
        Field camSourceField = ImageDetector.class.getDeclaredField("cameraSource");
        camSourceField.setAccessible(true);
        camSourceField.set(imageDetector, mockedCameraSource);
        verify(mockedCameraSource, never()).start();
        imageDetector.run();
        verify(mockedCameraSource, times(1)).start();
    }

    @Test
    public void testLBP() {
        Bitmap bitmap = BitmapFactory.decodeStream(getClass().getClassLoader().getResourceAsStream("unittest_image.png"));
        int[] lbpArray = imageDetector.mainLBP(bitmap);
        // TODO: Compare to file with correct data
    }

    @Test
    public void testLandMark() {
        Face mockedFace = mock(Face.class);
        List<Landmark> landmarks = new ArrayList<>();
        landmarks.add(new Landmark(new PointF(10, 12), Landmark.LEFT_EYE));
        landmarks.add(new Landmark(new PointF(20, 14), Landmark.RIGHT_EYE));
        landmarks.add(new Landmark(new PointF(15, 21), Landmark.BOTTOM_MOUTH));
        when(mockedFace.getLandmarks()).thenReturn(landmarks);
        JsonArray output = imageDetector.landMarks(mockedFace);
        Assert.assertEquals(3, output.size()); // 3 lines pr landmark
        JsonArray output0 = output.get(0).getAsJsonArray();
        Assert.assertEquals(""+Landmark.LEFT_EYE, output0.get(0).getAsString());
        Assert.assertEquals("10.0", output0.get(1).getAsString());
        Assert.assertEquals("12.0", output0.get(2).getAsString());
        JsonArray output1 = output.get(1).getAsJsonArray();
        Assert.assertEquals(""+ Landmark.RIGHT_EYE, output1.get(0).getAsString());
        Assert.assertEquals("20.0", output1.get(1).getAsString());
        Assert.assertEquals("14.0", output1.get(2).getAsString());
        JsonArray output2 = output.get(2).getAsJsonArray();
        Assert.assertEquals(""+Landmark.BOTTOM_MOUTH, output2.get(0).getAsString());
        Assert.assertEquals("15.0", output2.get(1).getAsString());
        Assert.assertEquals("21.0", output2.get(2).getAsString());
    }

    @Test
    public void testCleanUp() throws Exception {
        Field detectorField = ImageDetector.class.getDeclaredField("detector");
        detectorField.setAccessible(true);
        FaceDetector fakeDetector = mock(FaceDetector.class);
        detectorField.set(imageDetector, fakeDetector);
        Field cameraSourceField = ImageDetector.class.getDeclaredField("cameraSource");
        cameraSourceField.setAccessible(true);
        CameraSource fakeSource = mock(CameraSource.class);
        cameraSourceField.set(imageDetector, fakeSource);
        imageDetector.cleanUp();
        verify(fakeDetector, times(1)).destroy();
        verify(fakeSource, times(1)).release();
        Assert.assertNull(detectorField.get(imageDetector));
        Assert.assertNull(cameraSourceField.get(imageDetector));
    }

    @Test
    public void testStopFiresCameraSourceStop() throws Exception {
        Field camSourceField = ImageDetector.class.getDeclaredField("cameraSource");
        camSourceField.setAccessible(true);
        camSourceField.set(imageDetector, mockedCameraSource);
        verify(mockedCameraSource, never()).stop();
        imageDetector.terminate();
        verify(mockedCameraSource, times(1)).stop();
    }

    @Test
    public void testRunWithFailedCameraStart() throws Exception {
        Field camSourceField = ImageDetector.class.getDeclaredField("cameraSource");
        camSourceField.setAccessible(true);
        CameraSource fakeSource = mock(CameraSource.class);
        camSourceField.set(imageDetector, fakeSource);
        when(fakeSource.start()).thenThrow(new IOException("failed to open"));
        ShadowLog.clear();
        imageDetector.run();
        Assert.assertEquals("failed to open", ShadowLog.getLogs().get(0).throwable.getMessage());
    }

    @Test
    public void testFaceFoundWithNoLandmarks() {
        Face mockedFace = mock(Face.class);
        Frame mockedFrame = mock(Frame.class);
        Frame.Metadata mockedMetaData = mock(Frame.Metadata.class);
        when(mockedFrame.getMetadata()).thenReturn(mockedMetaData);
        when(mockedMetaData.getRotation()).thenReturn(Frame.ROTATION_90);
        when(mockedMetaData.getHeight()).thenReturn(96);
        when(mockedMetaData.getWidth()).thenReturn(96);
        InputStream is = getClass().getClassLoader().getResourceAsStream("unittest_image.png");
        Bitmap img = BitmapFactory.decodeStream(is);
        ByteBuffer byteBuffer = ByteBuffer.allocate(img.getByteCount());
        img.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        when(mockedFrame.getGrayscaleImageData()).thenReturn(byteBuffer);
        when(mockedFrame.getBitmap()).thenReturn(img);
        imageDetector.faceFound(mockedFace, mockedFrame);
    }

    @Test
    public void testFaceFoundWithCorrectLandmarks() {
        Face mockedFace = mock(Face.class);
        List<Landmark> landmarkList = new ArrayList<>();
        landmarkList.add(new Landmark(new PointF(20, 20), Landmark.LEFT_EYE));
        landmarkList.add(new Landmark(new PointF(80, 20), Landmark.RIGHT_EYE));
        landmarkList.add(new Landmark(new PointF(50, 70), Landmark.BOTTOM_MOUTH));
        when(mockedFace.getLandmarks()).thenReturn(landmarkList);
        Frame mockedFrame = mock(Frame.class);
        Frame.Metadata mockedMetaData = mock(Frame.Metadata.class);
        when(mockedFrame.getMetadata()).thenReturn(mockedMetaData);
        when(mockedMetaData.getRotation()).thenReturn(Frame.ROTATION_90);
        when(mockedMetaData.getHeight()).thenReturn(96);
        when(mockedMetaData.getWidth()).thenReturn(96);
        InputStream is = getClass().getClassLoader().getResourceAsStream("unittest_image.png");
        Bitmap img = BitmapFactory.decodeStream(is);
        ByteBuffer byteBuffer = ByteBuffer.allocate(img.getByteCount());
        img.copyPixelsToBuffer(byteBuffer);
        byteBuffer.rewind();
        when(mockedFrame.getGrayscaleImageData()).thenReturn(byteBuffer);
        when(mockedFrame.getBitmap()).thenReturn(img);
        imageDetector.faceFound(mockedFace, mockedFrame);
    }

    /*    @Test
    public void testAffineTransform() throws Exception {
        PointF leftEye = new PointF(30, 30);
        PointF rightEye = new PointF(60, 30);
        PointF mouth = new PointF(45, 45);
        Matrix matrix = ImageDetector.affineTransform(96, 96, leftEye, rightEye, mouth);
        float[] values = new float[9];

        matrix.getValues(values);
        Assert.assertEquals(-1.378228759765625, values[0], 0);
        Assert.assertEquals(0.053024037679036455, values[1], 0);
        Assert.assertEquals(107.65379333496094, values[2], 0);
        Assert.assertEquals(0.026751518249511955, values[3], 0);
        Assert.assertEquals(3.7801063537597654, values[4], 0);
        Assert.assertEquals(-99.25249767303468, values[5], 0);
    }*/
    /*
    01-12 14:19:25.575 32543-32543/com.telenor.possumexample I/AP:: src:[{30.0, 30.0}, {60.0, 30.0}, {45.0, 45.0}]
01-12 14:19:25.576 32543-32543/com.telenor.possumexample I/AP:: dest:[{67.89765167236328, 14.953238487243652}, {26.55078887939453, 15.755784034729004}, {48.01958084106445, 72.05610656738281}]
01-12 14:19:25.577 32543-32543/com.telenor.possumexample I/AP:: Row:0
01-12 14:19:25.577 32543-32543/com.telenor.possumexample I/AP:: Col:0 - val:[-1.378228759765625]
01-12 14:19:25.577 32543-32543/com.telenor.possumexample I/AP:: Col:1 - val:[0.053024037679036455]
01-12 14:19:25.578 32543-32543/com.telenor.possumexample I/AP:: Col:2 - val:[107.65379333496094]
01-12 14:19:25.578 32543-32543/com.telenor.possumexample I/AP:: Row:1
01-12 14:19:25.578 32543-32543/com.telenor.possumexample I/AP:: Col:0 - val:[0.026751518249511955]
01-12 14:19:25.578 32543-32543/com.telenor.possumexample I/AP:: Col:1 - val:[3.7801063537597654]
01-12 14:19:25.578 32543-32543/com.telenor.possumexample I/AP:: Col:2 - val:[-99.25249767303468]
    */
/*
cv::Mat cv::getAffineTransform( const Point2f src[], const Point2f dst[] ) {
    Mat M(2, 3, CV_64F), X(6, 1, CV_64F, M.ptr());
    double a[6*6], b[6];
    Mat A(6, 6, CV_64F, a), B(6, 1, CV_64F, b);

    for( int i = 0; i < 3; i++ ) {
        int j = i*12;
        int k = i*12+6;
        a[j] = a[k+3] = src[i].x;
        a[j+1] = a[k+4] = src[i].y;
        a[j+2] = a[k+5] = 1;
        a[j+3] = a[j+4] = a[j+5] = 0;
        a[k] = a[k+1] = a[k+2] = 0;
        b[i*2] = dst[i].x;
        b[i*2+1] = dst[i].y;
    }

    solve( A, B, X );
    return M;
}
*/
}