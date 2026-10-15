package com.wolfeleo2.subsy.ui.onboarding

import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.CURRENCY
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.DEMO
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.FIRST_ACTION
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.MODE
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.NAME
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.NOTIFICATIONS
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.PRIVACY
import com.wolfeleo2.subsy.ui.onboarding.OnboardingStep.WELCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingStepsTest {
    private fun forward(detectionOn: Boolean): List<OnboardingStep> = generateSequence(WELCOME) { nextStep(it, detectionOn) }.toList()

    @Test
    fun detectionOnWalksEveryStep() {
        assertEquals(listOf(WELCOME, MODE, CURRENCY, DEMO, PRIVACY, NOTIFICATIONS, NAME, FIRST_ACTION), forward(detectionOn = true))
    }

    @Test
    fun detectionOffSkipsPermissionsAndName() {
        assertEquals(listOf(WELCOME, MODE, CURRENCY, DEMO, FIRST_ACTION), forward(detectionOn = false))
    }

    @Test
    fun backRetracesForwardPathFromEveryStep() {
        for (on in listOf(true, false)) {
            val path = forward(on)
            path.zipWithNext().forEach { (from, to) -> assertEquals("$from, detection $on", from, previousStep(to, on)) }
        }
    }

    @Test
    fun backFromWelcomeLeavesTheApp() {
        assertNull(previousStep(WELCOME, detectionOn = false))
    }

    @Test
    fun nothingFollowsTheFirstAction() {
        assertNull(nextStep(FIRST_ACTION, detectionOn = true))
    }
}
