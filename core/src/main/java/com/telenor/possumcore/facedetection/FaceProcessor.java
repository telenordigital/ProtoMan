package com.telenor.possumcore.facedetection;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.FocusingProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;

public class FaceProcessor extends FocusingProcessor<Face> {
    public FaceProcessor(Detector<Face> detector, Tracker<Face> tracker) {
        super(detector, tracker);
    }

    @Override
    public int selectFocus(Detector.Detections detections) {
        return 0;
    }
}