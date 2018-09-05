package com.telenor.possumcore.neuralnetworks;

import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.google.gson.JsonArray;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

/**
 * Uses a given tensorFlow model as input from assets, takes a RGB array of an image and returns
 * a json array with the corresponding weights the model returns.
 */
public class TensorWeights extends TensorFlowInferenceInterface {
    // Note: Only supports armeabi-v7a as pr
    // https://github.com/miyosuda/TensorFlowAndroidDemo/tree/master/app/src/main/jniLibs/
    // This is hopefully not an issue.. except for robolectric emulator ofc...
    private static final String INPUT_NODE = "input:0";
    private static final String OUTPUT_NODE = "out:0";
    private static final int PIXEL_SIZE = 96;
    private static final int INPUT_SIZE = 3;
    private static final String[] OUTPUT_NODES = new String[]{OUTPUT_NODE};

    public TensorWeights(AssetManager assetManager, String s) {
        super(assetManager, s);
    }

    public JsonArray getWeights(Bitmap bitmap, long timestamp) {
        float[] result = new float[128];
        feed(INPUT_NODE, bitmapToFloatArray(bitmap), 1, PIXEL_SIZE, PIXEL_SIZE, INPUT_SIZE);
        run(OUTPUT_NODES);
        fetch(OUTPUT_NODE, result);
        JsonArray data = new JsonArray();
        data.add("" + timestamp);
        for (float weight : result) {
            data.add("" + weight);
        }
        return data;
    }

    static float[] bitmapToFloatArray(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
        if (width != height) {
            throw new java.lang.Error("BitmapToIntArray only makes sense on square images");
        }
        float[] array = new float[width * width * 3];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                array[i * width + 3 * j] = (pixels[i * j] >> 16) & 0xff;
                array[i * width + 3 * j + 1] = (pixels[i * j] >> 8) & 0xff;
                array[i * width + 3 * j + 2] = pixels[i * j] & 0xff;
            }
        }
        return array;
    }
}