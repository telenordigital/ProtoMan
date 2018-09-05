package com.telenor.possumgather.mockedDetectors;

import android.content.Context;
import android.support.annotation.NonNull;

import com.telenor.possumcore.detectors.Accelerometer;

public class MockedAccelerometer extends Accelerometer {
    public MockedAccelerometer(@NonNull Context context) {
        super(context);
    }
}
