package com.telenor.possumgather;

import android.content.Context;
import android.content.res.Resources;

import net.danlew.android.joda.JodaTimeAndroid;

import java.io.InputStream;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Duplicate of com.telenor.possumCore's TestUtils
 */
public class TestUtils {
    public static void initializeJodaTime() {
        Context context = mock(Context.class);
        Resources resources = mock(Resources.class);
        when(resources.openRawResource(anyInt())).thenReturn(mock(InputStream.class));
        when(context.getResources()).thenReturn(resources);
        when(context.getApplicationContext()).thenReturn(context);
        JodaTimeAndroid.init(context);
    }
}