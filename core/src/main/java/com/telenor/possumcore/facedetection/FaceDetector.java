package com.telenor.possumcore.facedetection;

import android.support.annotation.NonNull;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;


public class FaceDetector extends Detector<Face> {
    private Detector<Face> delegate;
    private IFaceFound listener;

    public FaceDetector(@NonNull Detector<Face> delegate, @NonNull IFaceFound listener) {
        this.delegate = delegate;
        this.listener = listener;
    }

    public boolean isReleased() {
        return delegate == null;
    }

    @Override
    public SparseArray<Face> detect(Frame frame) {
        if (delegate == null || frame == null) {
            return new SparseArray<>();
        }
        SparseArray<Face> faces = delegate.detect(frame);
        // When you need to change it, frame should be sent - not the byte array
        if (listener != null && faces.size() > 0) {
            listener.faceFound(faces.get(faces.keyAt(0)), frame);
        }
        return faces;
    }

    /**
     * Terminates the detector, releasing it. Should be done in cleanup.
     */
    public void destroy() {
        if (!isReleased()) {
            delegate.release();
            delegate = null;
        }
    }

    public boolean isOperational() {
        return delegate.isOperational();
    }
}