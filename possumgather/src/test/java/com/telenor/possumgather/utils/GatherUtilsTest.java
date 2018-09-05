package com.telenor.possumgather.utils;

import com.telenor.possumgather.BuildConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(constants = BuildConfig.class)
@RunWith(RobolectricTestRunner.class)
public class GatherUtilsTest {
    @Before
    public void setUp() {
    }
    @After
    public void tearDown() {

    }
    @Test
    public void testGetFiles() {
        //GatherUtils.getFiles()
    }
    @Test
    public void testCreateZipStream() {
        //GatherUtils.createZipStream()
    }
    @Test
    public void testStorageCatalogue() {
        //GatherUtils.storageCatalogue()
    }
}