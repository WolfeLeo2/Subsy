package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.update.compareVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareVersionsTest {
    @Test
    fun comparesNumbersNotText() {
        assertTrue(compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(compareVersions("0.2.0", "0.10.0") < 0)
    }

    @Test
    fun missingPartsCountAsZero() {
        assertEquals(0, compareVersions("1.2", "1.2.0"))
    }
}
