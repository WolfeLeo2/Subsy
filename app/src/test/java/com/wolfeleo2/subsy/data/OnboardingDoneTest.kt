package com.wolfeleo2.subsy.data

import com.wolfeleo2.subsy.data.model.AppMode
import com.wolfeleo2.subsy.data.repository.onboardingDone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDoneTest {
    @Test
    fun freshInstallIsNotDone() {
        assertFalse(onboardingDone(stored = null, appMode = null, detectionIntroSeen = false))
    }

    @Test
    fun modeSetAndIntroSeenIsDone() {
        assertTrue(onboardingDone(stored = null, appMode = AppMode.SUBSCRIPTIONS, detectionIntroSeen = true))
    }

    @Test
    fun modeSetWithoutIntroIsNotDone() {
        assertFalse(onboardingDone(stored = null, appMode = AppMode.ALL_SPENDING, detectionIntroSeen = false))
    }

    @Test
    fun theStoredFlagWins() {
        assertTrue(onboardingDone(stored = true, appMode = null, detectionIntroSeen = false))
        assertFalse(onboardingDone(stored = false, appMode = AppMode.SUBSCRIPTIONS, detectionIntroSeen = true))
    }
}
