package com.telenor.possumcore.detectors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresPermission;
import android.util.Log;

import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.telenor.possumcore.PossumCore;
import com.telenor.possumcore.abstractdetectors.AbstractDetector;
import com.telenor.possumcore.constants.DetectorType;
import com.telenor.possumcore.facedetection.FaceDetector;
import com.telenor.possumcore.facedetection.FaceProcessor;
import com.telenor.possumcore.facedetection.FaceTracker;
import com.telenor.possumcore.facedetection.IFaceFound;
import com.telenor.possumcore.interfaces.IDetectorChange;
import com.telenor.possumcore.neuralnetworks.TensorWeights;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;

/**
 * Uses your back camera to try to get a facial assessment, utilizing image recognition to see
 * whether you are yourself or not
 */
public class ImageDetector extends AbstractDetector implements IFaceFound {
    private static TensorWeights tensorFlowInterface;
    private static FaceDetector detector; // To prevent changes during configChanges it is static
    private CameraSource cameraSource;
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int OUTPUT_BMP_WIDTH = 96;
    private static final int OUTPUT_BMP_HEIGHT = 96;
    private static final int MINIMUM_FACE_INTERVAL = 500; // Least amount of milliseconds between accepting new face
    private long lastFaceProcessed;
    private JsonParser parser;
    private Gson gson;
    private static final String lbpDataSet = "image_lbp";

    public ImageDetector(@NonNull Context context, @NonNull String modelName) {
        this(context, modelName, null);
    }
    public ImageDetector(@NonNull Context context, @NonNull String modelName, IDetectorChange listener) {
        super(context, listener);
        parser = new JsonParser();
        gson = new Gson();
        setupCameraSource();
        createDataSet(lbpDataSet);
        try {
            tensorFlowInterface = createTensor(context.getAssets(), modelName);
        } catch (Exception e) {
            Log.e(tag, "AP: Failed to initialize tensorFlow or openCV:", e);
            PossumCore.addLogEntry(context(), "Failed to initialize tensorflow:"+e.getLocalizedMessage());
        }
    }

    @Override
    public int queueLimit(@NonNull String key) {
        if (key.equals(lbpDataSet)) return 5;
        return 10; // Default set
    }

    /**
     * The face detector is the actual detector finding the face in a video stream. It handles
     * setting up google vision, setting the custom face detector to report faces in the interface
     * and the processor handling it.
     * <p>
     * TODO: WARNING: It will require special treatment for configuration changes (viewport rotation)
     */
    private void setupFaceDetector() {
        if (detector == null || detector.isReleased()) {
            com.google.android.gms.vision.face.FaceDetector.Builder visionBuilder = new com.google.android.gms.vision.face.FaceDetector.Builder(context());
            visionBuilder.setLandmarkType(com.google.android.gms.vision.face.FaceDetector.ALL_LANDMARKS);
            visionBuilder.setTrackingEnabled(false);
            visionBuilder.setProminentFaceOnly(true);
            visionBuilder.setMode(com.google.android.gms.vision.face.FaceDetector.FAST_MODE);
            com.google.android.gms.vision.face.FaceDetector googleFaceDetector = visionBuilder.build();
            detector = new FaceDetector(googleFaceDetector, this);
            detector.setProcessor(new FaceProcessor(detector, new FaceTracker()));
        }
    }

    /**
     * Sets up the camera source (and the face detector before it). Note that this process is time
     * consuming and should be done as early as possible (taking about 2 seconds to complete after
     * it starts). This precludes setting it up for each run()/scan as it would take most of the
     * time required to identity faces just to set it up.
     */
    private void setupCameraSource() {
        setupFaceDetector();
        if (cameraSource != null) {
            cameraSource.release();
        }
        cameraSource = new CameraSource.Builder(context(), detector)
                .setFacing(CameraSource.CAMERA_FACING_FRONT)
                .setRequestedPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT)
                .setRequestedFps(30)
                .build();
    }

    @Override
    public int detectorType() {
        return DetectorType.Image;
    }

    @Override
    public String detectorName() {
        return "image";
    }

    @SuppressWarnings("MissingPermission")
    @RequiresPermission(Manifest.permission.CAMERA)
    @Override
    public void run() {
        super.run();
        if (isEnabled() && isAvailable()) {
            //didFindFace = false;
            try {
                cameraSource.start();
                PossumCore.addLogEntry(context(), "Camera is started");
            } catch (IOException e) {
                PossumCore.addLogEntry(context(), "Unable to start camera:"+e.getLocalizedMessage());
                Log.i(tag, "AP: IO:", e);
            }
        } else {
            PossumCore.addLogEntry(context(), "Unable to start cam:"+isEnabled()+","+isAvailable());
        }
    }

    @SuppressWarnings("MissingPermission")
    @Override
    public void terminate() {
//        PossumCore.addLogEntry(context(), didFindFace?"Did find face during rotation":"No face found during rotation");
        if (isPermitted()) {
            if (cameraSource != null) {
                cameraSource.stop();
                PossumCore.addLogEntry(context(), "Camera is stopped");
            }
        }
    }

    @Override
    public boolean isEnabled() {
        return tensorFlowInterface != null && context().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    @Override
    public void faceFound(Face face, Frame frame) {
        if (face == null) {
            PossumCore.addLogEntry(context(), "Face is null from detector");
            return;
        }
        if (now() - lastFaceProcessed < MINIMUM_FACE_INTERVAL) return;
        lastFaceProcessed = now();
        PointF leftEye = null;
        PointF rightEye = null;
        PointF mouth = null;
        // TODO: IMPORTANT: Some devices have differing output from CameraSource. Some output landscape, other portrait. How to handle this?
        int orientation = -1;
        switch (frame.getMetadata().getRotation()) {
            case Frame.ROTATION_0:
                orientation = 0;
                break;
            case Frame.ROTATION_90:
                orientation = 90;
                break;
            case Frame.ROTATION_180:
                orientation = 180;
                break;
            case Frame.ROTATION_270:
                orientation = 270;
                break;
        }
        byte[] imgBytes = getBytesFromFrame(frame);
        Bitmap imageBeforeProcess = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.length);
        final Bitmap imageProcessed;
        if (orientation != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(orientation);
            imageProcessed = Bitmap.createBitmap(imageBeforeProcess, 0, 0, imageBeforeProcess.getWidth(), imageBeforeProcess.getHeight(), matrix, false);
        } else imageProcessed = imageBeforeProcess;

        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == Landmark.LEFT_EYE) leftEye = landmark.getPosition();
            else if (landmark.getType() == Landmark.RIGHT_EYE) rightEye = landmark.getPosition();
            else if (landmark.getType() == Landmark.BOTTOM_MOUTH) mouth = landmark.getPosition();
        }
        if (leftEye != null && rightEye != null && mouth != null) {
            PointF centroid = new PointF((rightEye.x + leftEye.x + mouth.x) / 3, (rightEye.y + leftEye.y + mouth.y) / 3);
            float diffX = 1.5f * Math.abs(leftEye.x - rightEye.x);
            RectF faceFrame = new RectF(centroid.x - diffX, centroid.y - diffX, centroid.x + diffX, centroid.y + diffX);
            if (faceFrame.left < 0 || faceFrame.top < 0) {
                // Unable to get a frame around the necessary area
                return;
            }
            Bitmap fixedImage = Bitmap.createBitmap(imageProcessed, (int) faceFrame.left, (int) faceFrame.top, (int) faceFrame.width(), (int) faceFrame.height());
            PointF movedLeftEye = new PointF(leftEye.x - faceFrame.left, leftEye.y - faceFrame.top);
            PointF movedRightEye = new PointF(rightEye.x - faceFrame.left, rightEye.y - faceFrame.top);
            PointF movedMouth = new PointF(mouth.x - faceFrame.left, mouth.y - faceFrame.top);

            Bitmap alignedFace = alignFace(fixedImage, movedLeftEye, movedRightEye, movedMouth);
            if (alignedFace == null) {
                return;
            }
//            didFindFace = true;
            final Bitmap scaledOutput = Bitmap.createScaledBitmap(alignedFace, OUTPUT_BMP_WIDTH, OUTPUT_BMP_HEIGHT, false);

            long nowTimestamp = now();

            // Tensor weights
            try {
                streamData(tensorFlowInterface.getWeights(scaledOutput, nowTimestamp));
            } catch (Exception e) {
                PossumCore.addLogEntry(context(), "Failed to stream tensorflow:"+e.getLocalizedMessage());
            }
            // LBP array
            try {
                JsonArray lbpArray = new JsonArray();
                lbpArray.add("" + nowTimestamp);
                lbpArray.add(parser.parse(gson.toJson(mainLBP(scaledOutput))));
                lbpArray.add(landMarks(face));
                streamData(lbpArray, lbpDataSet);
            } catch (Exception e) {
                PossumCore.addLogEntry(context(), "Failed to handle LBP:"+e.getLocalizedMessage());
            }
            PossumCore.addLogEntry(context(), "Face determined and processed");
//            Log.i(tag, "AP: Processed face");
        }
    }

    int[] mainLBP(Bitmap image) {
        // Convert the input image to a matrix image
        double[][] realImage = imageConversion(image);

        // Calculate the number of squares
        int bw = 8; // Magic number
        int bh = 8; // Magic number
        int nbx = (int) Math.floor(realImage.length / bw);
        int nby = (int) Math.floor(realImage.length / bh);
        // Create the LBP vector
        return LBPVector(realImage, nbx, nby, bw, bh);
    }

    public JsonArray landMarks(Face face) {
        JsonArray landmarks = new JsonArray();
        for (Landmark landmark : face.getLandmarks()) {
            JsonArray landmarkSet = new JsonArray();
            landmarkSet.add(""+ landmark.getType());
            landmarkSet.add(""+landmark.getPosition().x);
            landmarkSet.add(""+landmark.getPosition().y);
            landmarks.add(landmarkSet);
        }
        return landmarks;
    }

    // Function to convert a image to a double matrix image
    private double[][] imageConversion(Bitmap image) {
        if (image.getWidth() != OUTPUT_BMP_HEIGHT || image.getHeight() != OUTPUT_BMP_HEIGHT)
            image = Bitmap.createScaledBitmap(image, OUTPUT_BMP_WIDTH, OUTPUT_BMP_HEIGHT, true);
        double[][] realImage = new double[image.getHeight()][image.getWidth()];
        for (int x = 0; x < image.getWidth(); x++)
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getPixel(x, y);
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                realImage[y][x] = (r + g + b) / 3 + 1;
            }
        return realImage;
    }

    // Function to create the final LBP vector
    private int[] LBPVector(double[][] realImage, int nbx, int nby, int bw, int bh) {
        int samples = 16;
        int max = samples * (samples - 1) + 3;
        int index = 0;
        Vector<Integer> table = new Vector<>((int) Math.pow(2, samples));

        for (int i = 0; i <= (Math.pow(2, samples)) - 1; i++) {
            int shift = (i % 32768) << 1;
            //int shift = (i%128) << 1;
            int position = (i >> (samples - 1));

            int j = shift + position;

            int xor = i ^ j;
            int numt = 0;
            for (int s = 0; s < samples; s++) {
                byte tt = (byte) ((xor >> (s) & 1));
                numt = numt + tt;
            }

            if (numt <= 2) {
                table.add(i, index);
                index += 1;

            } else {
                table.add(i, max - 1);
            }
        }


        int width1 = realImage.length;
        int height1 = realImage.length;

        int[] LBPUTexDesc = new int[max * nbx * nby];
        int[] lim1 = new int[(int) Math.ceil(width1 / bw)];
        int[] lim2 = new int[width1 / bw];
        int[] lim3 = new int[(int) Math.ceil(height1 / bh)];
        int[] lim4 = new int[height1 / bh];

        int cont = 0;
        int conti = 0;
        for (int ii = 0; ii < width1; ii = ii + bw) {
            if (conti < lim1.length)
                lim1[conti] = ii;

            if (conti < lim2.length)
                lim2[conti] = bw + ii - 1;
            conti++;
        }
        conti = 0;
        for (int ii = 0; ii < height1; ii = ii + bh) {
            if (conti < lim3.length)
                lim3[conti] = ii;
            if (conti < lim4.length)
                lim4[conti] = bh + ii - 1;
            conti++;
        }


        for (int ii = 0; ii < nbx; ii++) {
            for (int jj = 0; jj < nby; jj++) {
                double[][] imregion = new double[bw][bh];

                int ci = 0;
                int cj = 0;
                for (int i = lim1[ii]; i <= lim2[ii]; i++) {
                    for (int j = lim3[jj]; j <= lim4[jj]; j++) {
                        imregion[cj][ci] = realImage[j][i];
                        cj++;
                    }
                    cj = 0;
                    ci++;
                }

                int[] finalHist = lbpAux(imregion, table, max, samples);
                if (finalHist == null) {
                    // TODO: What here?
                    return null;
                }
                for (int contador = 0; contador < finalHist.length; contador++) {
                    LBPUTexDesc[contador + cont] = finalHist[contador];
                }
                cont = cont + finalHist.length;

            }
        }
        return LBPUTexDesc;
    }

    // Function to create the LBP vector of each region
    private static int[] lbpAux(double[][] imregion, Vector<Integer> table, int max, int samples) {
        double radius = 2;

        double angle = 2 * Math.PI / samples;

        double[][] points = new double[samples][2];
        double min_x = 0;
        double min_y = 0;
        double max_x = 0;
        double max_y = 0;

        for (int n1 = 0; n1 < samples; n1++) {
            for (int n2 = 0; n2 < 1; n2++) {
                points[n1][n2] = -radius * Math.sin((n1) * angle);
                points[n1][n2 + 1] = radius * Math.cos((n1) * angle);

                if (points[n1][n2] < min_x) {
                    min_x = points[n1][n2];
                }
                if (points[n1][n2 + 1] < min_y) {
                    min_y = points[n1][n2 + 1];
                }
                if (points[n1][n2] > max_x) {
                    max_x = points[n1][n2];
                }
                if (points[n1][n2 + 1] > max_y) {
                    max_y = points[n1][n2 + 1];
                }
            }
        }

        long max_y_round = Math.round(max_y);
        long min_y_round = Math.round(min_y);
        long max_x_round = Math.round(max_x);
        long min_x_round = Math.round(min_x);

        int coord_x = (int) (1 - min_x_round);
        int coord_y = (int) (1 - min_y_round);

        if (imregion.length < (max_y_round - min_y_round + 1) && (imregion.length) < (max_x_round - min_x_round + 1)) {
            System.out.println("Error, image too small");
            // TODO: What here?
            return null;
        }

        double dx = imregion.length - (max_x_round - min_x_round + 1);
        double dy = imregion.length - (max_y_round - min_y_round + 1);


        double[][] C = new double[(int) (dy + 1)][(int) (dx + 1)];

        for (int jj = coord_x - 1; jj <= coord_x + dx - 1; jj++) {
            for (int tt = coord_y - 1; tt <= coord_y + dy - 1; tt++) {
                C[jj - (coord_x - 1)][tt - (coord_y - 1)] = imregion[jj][tt];
            }
        }

        double[][] result = new double[(int) (dy + 1)][(int) (dx + 1)];
        double[][] result2 = new double[(int) (dy + 1)][(int) (dx + 1)];


        int[][] D = new int[(int) (dy + 1)][(int) (dx + 1)];
        for (int i = 0; i < samples; i++) {

            double y = (points[i][0] + coord_y);
            double x = (points[i][1] + coord_x);

            double fy = Math.floor(y);
            double cy = Math.ceil(y);
            double ry = Math.round(y);
            double fx = Math.floor(x);
            double cx = Math.ceil(x);
            double rx = Math.round(x);


            if (Math.abs(x - rx) < Math.pow(10, -6) && Math.abs(y - ry) < Math.pow(10, -6)) {
                double[][] N = new double[(int) (dy + 1)][(int) (dx + 1)];

                for (int jj = (int) rx - 1; jj <= rx + dx - 1; jj++) {
                    for (int tt = (int) ry - 1; tt <= ry + dy - 1; tt++) {
                        N[(int) (tt - (ry - 1))][(int) (jj - (rx - 1))] = imregion[tt][jj];
                    }
                }
                for (int jj = 0; jj < (dy + 1); jj++) {
                    for (int tt = 0; tt < (dx + 1); tt++) {
                        if (N[jj][tt] >= C[jj][tt]) {
                            D[jj][tt] = 1;
                        } else {
                            D[jj][tt] = 0;
                        }
                    }
                }
            } else {

                double ty = y - fy;
                double tx = x - fx;

                double w1 = Math.round((1 - tx) * (1 - ty) * 1000000);
                w1 = w1 / 1000000;
                double w2 = Math.round(tx * (1 - ty) * 1000000);
                w2 = w2 / 1000000;
                double w3 = Math.round((1 - tx) * ty * 1000000);
                w3 = w3 / 1000000;
                double w4 = Math.round((1 - w1 - w2 - w3) * 1000000);
                w4 = w4 / 1000000;

                double[][] N4 = new double[(int) (dy + 1)][(int) (dx + 1)];
                double[][] N1 = new double[(int) (dy + 1)][(int) (dx + 1)];
                double[][] N2 = new double[(int) (dy + 1)][(int) (dx + 1)];
                double[][] N3 = new double[(int) (dy + 1)][(int) (dx + 1)];
                double[][] N = new double[(int) (dy + 1)][(int) (dx + 1)];

                //W1
                for (int jj = (int) fx - 1; jj <= fx + dx - 1; jj++) {
                    for (int tt = (int) fy - 1; tt <= fy + dy - 1; tt++) {
                        N1[(int) (tt - (fy - 1))][(int) (jj - (fx - 1))] = w1 * imregion[tt][jj];
                    }
                }

                //W2
                for (int jj = (int) cx - 1; jj <= cx + dx - 1; jj++) {
                    for (int tt = (int) fy - 1; tt <= fy + dy - 1; tt++) {
                        N2[(int) (tt - (fy - 1))][(int) (jj - (cx - 1))] = w2 * imregion[tt][jj];
                    }
                }

                //W3
                for (int jj = (int) fx - 1; jj <= fx + dx - 1; jj++) {
                    for (int tt = (int) cy - 1; tt <= cy + dy - 1; tt++) {
                        N3[(int) (tt - (cy - 1))][(int) (jj - (fx - 1))] = w3 * imregion[tt][jj];
                    }
                }

                //W4
                for (int jj = (int) cx - 1; jj <= cx + dx - 1; jj++) {
                    for (int tt = (int) cy - 1; tt <= cy + dy - 1; tt++) {
                        N4[(int) (tt - (cy - 1))][(int) (jj - (cx - 1))] = w4 * imregion[tt][jj];
                    }
                }

                for (int jj = 0; jj < (dy + 1); jj++) {
                    for (int tt = 0; tt < (dx + 1); tt++) {
                        //System.out.println(N1[jj][tt]+ " and " +N2[jj][tt] + " and " +N3[jj][tt] +  " and " +N4[jj][tt]);
                        N[jj][tt] = N1[jj][tt] + N2[jj][tt] + N3[jj][tt] + N4[jj][tt];
                        double ex = Math.round(N[jj][tt] * 10000);
                        N[jj][tt] = ex / 10000;
                    }
                }

                for (int jj = 0; jj < (dy + 1); jj++) {
                    for (int tt = 0; tt < (dx + 1); tt++) {
                        if (N[jj][tt] >= C[jj][tt]) {
                            D[jj][tt] = 1;
                        } else {
                            D[jj][tt] = 0;
                        }
                    }
                }

            } //End else

            double v = Math.pow(2, (i));
            for (int jj = 0; jj < (dy + 1); jj++) {
                for (int tt = 0; tt < (dx + 1); tt++) {
                    result[jj][tt] = result[jj][tt] + (v * D[jj][tt]);
                }
            }
        }

        for (int jj = 0; jj < (dy + 1); jj++) {
            for (int tt = 0; tt < (dx + 1); tt++) {
                result2[jj][tt] = table.get((int) (result[jj][tt]));
            }
        }

        int[] finalResult = new int[max];

        for (int jj = 0; jj < (dy + 1); jj++) {
            for (int tt = 0; tt < (dx + 1); tt++) {
                int position = (int) (result2[jj][tt]);
                finalResult[position] = finalResult[position] + 1;
            }
        }

        return finalResult;
    }

    /**
     * Method for converting a frame from google's vision api to a byteArray
     *
     * @param frame a google vision frame
     * @return a byte array of the image
     */
    private byte[] getBytesFromFrame(Frame frame) {
        int height = frame.getMetadata().getHeight();
        int width = frame.getMetadata().getWidth();
        YuvImage yuvimage = new YuvImage(frame.getGrayscaleImageData().array(), ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(0, 0, width, height), 100, byteArrayOutputStream); // Where 100 is the quality of the generated jpeg
        return byteArrayOutputStream.toByteArray();
    }

    protected TensorWeights createTensor(AssetManager assetManager, String modelName) {
        return new TensorWeights(assetManager, modelName);
    }

    @Override
    public void cleanUp() {
        super.cleanUp();
        PossumCore.addLogEntry(context(), "Terminated/Cleaned camera");
        if (detector != null) {
            detector.destroy();
            detector = null;
        }
        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
    }

    private Bitmap alignFace(@NonNull Bitmap face, PointF leftEye, PointF rightEye, PointF mouth) {
        float[] sourcePoints = {leftEye.x,rightEye.x,mouth.x,leftEye.y,rightEye.y,mouth.y,1,1,1};

        double dimX = face.getWidth();
        double dimY = face.getHeight();

        float[] destPoints = {(float) (dimX*0.70726717), (float) (dimX*0.27657071), (float) (dimX*0.50020397), (float) (dimY*0.1557629), (float) (dimY*0.16412275), (float) (dimY*0.75058442),1,1,1};
        float[] sourcePointsInverse = calculateInverseMatrix (sourcePoints);
        float[] transformation = calculateTransformation(destPoints,sourcePointsInverse);

        RectF rect = new RectF(0, 0, face.getWidth(), face.getHeight());
        RectF dst = new RectF();

        Matrix matrixNew = new Matrix();
        matrixNew.setValues(transformation);
        matrixNew.mapRect(dst,rect);
        Bitmap scaled = Bitmap.createBitmap(face,0,0,face.getWidth(),face.getHeight(),matrixNew,true);
        Bitmap transformedFace = Bitmap.createBitmap(scaled,(int)-(dst.left),(int)-(dst.top),face.getWidth(),face.getHeight());
        return transformedFace;
    }

    private float[] calculateInverseMatrix(float[] v) {
        float determinantM = (v[0]*v[4]*v[8])+
                             (v[3]*v[7]*v[2])+
                             (v[1]*v[5]*v[6])-
                             (v[2]*v[4]*v[6])-
                             (v[1]*v[3]*v[8])-
                             (v[5]*v[7]*v[0]);

        float[] adjM = new float[9];
        adjM[0] = (v[4]*v[8])-(v[5]*v[7]);
        adjM[1] = (v[2]*v[7])-(v[1]*v[8]);
        adjM[2] = (v[1]*v[5])-(v[4]*v[2]);
        adjM[3] = (v[6]*v[5])-(v[3]*v[8]);
        adjM[4] = (v[0]*v[8])-(v[2]*v[6]);
        adjM[5] = (v[2]*v[3])-(v[0]*v[5]);
        adjM[6] = (v[3]*v[7])-(v[4]*v[6]);
        adjM[7] = (v[6]*v[1])-(v[0]*v[7]);
        adjM[8] = (v[0]*v[4])-(v[3]*v[1]);


        float[] mInverse = new float[9];
        mInverse[0] = adjM[0]/determinantM;
        mInverse[1] = adjM[1]/determinantM;
        mInverse[2] = adjM[2]/determinantM;
        mInverse[3] = adjM[3]/determinantM;
        mInverse[4] = adjM[4]/determinantM;
        mInverse[5] = adjM[5]/determinantM;
        mInverse[6] = adjM[6]/determinantM;
        mInverse[7] = adjM[7]/determinantM;
        mInverse[8] = adjM[8]/determinantM;

        return mInverse;
    }

    private float[] calculateTransformation(float[] destPoints, float[] sourcePointsInverse) {
        float[] tranformation = new float[9];
        tranformation[0] = (destPoints[0]*sourcePointsInverse[0]) + (destPoints[1]*sourcePointsInverse[3])+ (destPoints[2]*sourcePointsInverse[6]);
        tranformation[1] = (destPoints[0]*sourcePointsInverse[1]) + (destPoints[1]*sourcePointsInverse[4])+ (destPoints[2]*sourcePointsInverse[7]);
        tranformation[2] = (destPoints[0]*sourcePointsInverse[2]) + (destPoints[1]*sourcePointsInverse[5])+ (destPoints[2]*sourcePointsInverse[8]);
        tranformation[3] = (destPoints[3]*sourcePointsInverse[0]) + (destPoints[4]*sourcePointsInverse[3])+ (destPoints[5]*sourcePointsInverse[6]);
        tranformation[4] = (destPoints[3]*sourcePointsInverse[1]) + (destPoints[4]*sourcePointsInverse[4])+ (destPoints[5]*sourcePointsInverse[7]);
        tranformation[5] = (destPoints[3]*sourcePointsInverse[2]) + (destPoints[4]*sourcePointsInverse[5])+ (destPoints[5]*sourcePointsInverse[8]);
        tranformation[6] = 0;
        tranformation[7] = 0;
        tranformation[8] = 1;

        return tranformation;
    }

    @Override
    public String requiredPermission() {
        return Manifest.permission.CAMERA;
    }
}