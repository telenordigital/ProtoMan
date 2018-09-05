package com.telenor.possumgather.upload;

import android.content.Intent;

import com.telenor.possumgather.BuildConfig;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class AmazonUploadServiceTest {
    private AmazonUploadService service;
    @Before
    public void setUp() {
        service = new AmazonUploadService();
    }

    @After
    public void tearDown() {
        service = null;
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(service);
    }

    @Test
    public void testBindReturnsNull() {
        Assert.assertNull(service.onBind(new Intent()));
    }
}