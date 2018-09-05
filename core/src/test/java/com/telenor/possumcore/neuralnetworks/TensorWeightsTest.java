package com.telenor.possumcore.neuralnetworks;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.telenor.possumcore.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class TensorWeightsTest {
    // TODO: Find a way to test this with unit tests. Problem:
    // "Emulator", robolectric, fires a x86_64 jdk to run tests, but tensorFlow does not support
    // this architecture - only armeabi-v7a.
    // (Check https://github.com/miyosuda/TensorFlowAndroidDemo/tree/master/app/src/main/jniLibs/)
    // Also check http://www.sureshjoshi.com/mobile/android-junit-native-libraries/
    // This makes test impossible unless I can get tensorFlow
    // to run in another architecture OR make robolectric run in a different architecture. So yeah,
    // make tensorFlow run in x86_64 or bust. Perhaps build it with that architecture and include
    // .so file into test?
    @Mock
    private AssetManager mockedAssetManager;
    private TensorWeights tensorWeights;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
//        tensorWeights = new TensorWeights(mockedAssetManager, "fakeModel");
    }

    @After
    public void tearDown() {
//        tensorWeights = null;
    }

    @Test
    public void testInitialize() {
//        Assert.assertNotNull(mockedTensorWeights);
    }

    @Test
    public void testGetWeights() {
        // TODO: Implement
    }

    @Test
    public void testBitmapToIntArray() {
        Bitmap bitmap = BitmapFactory.decodeStream(getClass().getClassLoader().getResourceAsStream("unittest_image.png"));
        float[] array = TensorWeights.bitmapToFloatArray(bitmap);
        // TODO: Compare to result
    }
}