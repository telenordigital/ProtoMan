package com.telenor.possumgather.utils;

import com.telenor.possumgather.BuildConfig;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.OutputStream;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class CountingOutputStreamTest {
    @Mock
    private OutputStream mockedOutputStream;
    private CountingOutputStream countingOutputStream;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        countingOutputStream = new CountingOutputStream(mockedOutputStream);
    }

    @After
    public void tearDown() {
        countingOutputStream = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(countingOutputStream);
    }
}