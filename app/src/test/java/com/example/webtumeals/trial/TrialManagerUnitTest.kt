package com.example.webtumeals.trial

import com.example.webtumeals.FakeSharedPreferences
import com.example.webtumeals.data.storage.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrialManagerUnitTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var userPrefs: UserPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        userPrefs = UserPreferences(fakePrefs)
    }

    @Test
    fun testUserPreferences_trialKeys() {
        assertEquals(0L, userPrefs.trialFirstLaunchTime)
        assertFalse(userPrefs.isTrialExpiredPermanently)
        assertFalse(userPrefs.isDeveloperBypass)

        userPrefs.trialFirstLaunchTime = 123456789L
        userPrefs.isTrialExpiredPermanently = true
        userPrefs.isDeveloperBypass = true

        assertEquals(123456789L, userPrefs.trialFirstLaunchTime)
        assertTrue(userPrefs.isTrialExpiredPermanently)
        assertTrue(userPrefs.isDeveloperBypass)
    }

    @Test
    fun testUserPreferences_clear_preservesTrialLockoutState() {
        userPrefs.matricule = "202135012345"
        userPrefs.password = "secret123"
        userPrefs.isTrialExpiredPermanently = true
        userPrefs.trialFirstLaunchTime = 1000L

        userPrefs.clear()

        // Credentials must be wiped
        assertEquals("", userPrefs.matricule)
        assertEquals("", userPrefs.password)
        assertFalse(userPrefs.hasCredentials)

        // Lockout marker must remain preserved
        assertTrue(userPrefs.isTrialExpiredPermanently)
        assertEquals(1000L, userPrefs.trialFirstLaunchTime)
    }

    @Test
    fun testTrialManager_durationConstants() {
        assertEquals(7, TrialManager.TRIAL_DURATION_DAYS)
        assertEquals(7L * 24 * 60 * 60 * 1000L, TrialManager.TRIAL_DURATION_MS)
        assertEquals(1760572800000L, TrialManager.GLOBAL_TRIAL_EXPIRATION_EPOCH)
    }
}
