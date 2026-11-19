package com.androidharness.app

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppMemoryPolicyTest {
    @Test
    fun uiHiddenIsNotTreatedAsMemoryPressure() {
        assertFalse(AppMemoryPolicy.shouldReleaseCachedModel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN))
    }

    @Test
    fun realMemoryPressureStillReleasesTheCachedModel() {
        assertTrue(AppMemoryPolicy.shouldReleaseCachedModel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW))
        assertTrue(AppMemoryPolicy.shouldReleaseCachedModel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL))
        assertTrue(AppMemoryPolicy.shouldReleaseCachedModel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND))
    }

    @Test
    fun ordinaryTrimLevelDoesNotReleaseTheModel() {
        assertFalse(AppMemoryPolicy.shouldReleaseCachedModel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }
}
