package com.telenor.possumcore.constants;

import org.junit.Assert;
import org.junit.Test;

public class ConstantsTest {
    @Test
    public void testValues() {
        Constants constants = new Constants();
        Assert.assertNotNull(constants);
        Assert.assertEquals(1337, Constants.PermissionsRequestCode);
    }
}
