package com.telenor.possumcore.interfaces;

import com.telenor.possumcore.abstractdetectors.AbstractDetector;

/**
 * Interface for changes in detectors
 */
public interface IDetectorChange {
    void detectorChanged(AbstractDetector detector);
}