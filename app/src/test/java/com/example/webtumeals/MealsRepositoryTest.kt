package com.example.webtumeals

import android.content.SharedPreferences
import com.example.webtumeals.data.MealsRepository
import com.example.webtumeals.data.network.WebEtuClient
import com.example.webtumeals.data.storage.UserPreferences
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MealsRepositoryTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var client: WebEtuClient
    private lateinit var baseUrl: String
    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        baseUrl = mockServer.url("/api").toString().removeSuffix("/")
        client = WebEtuClient(
            primaryUrl = baseUrl,
            fallbackUrl = baseUrl,
            onouUrl = baseUrl
        )
        fakePrefs = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun testAutoBookForDate_semiAutoWithLunchOnlyDepot_booksLunch() = runTest {
        // Mock login
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_tok","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(16L, 0L)

        // Mock reservations (empty)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        // Mock depots: Depot only serves LUNCH (campus restaurant)
        val depotsJson = """{
            "depots": [
                {"id": 801, "nameFR": "Resto Campus", "nameAR": "مطعم", "isRu": 0, "breakfast": false, "lunch": true, "dinner": false}
            ]
        }"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(depotsJson))

        // Mock book response
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))

        val prefs = UserPreferences(fakePrefs).apply {
            matricule = "user"
            password = "pass"
            preferredRestaurantId = 801L
            automationMode = "SEMI_AUTO"
        }
        val repository = MealsRepository(client, prefs)

        // Semi auto originally requests BREAKFAST and DINNER, but depot only serves LUNCH
        val result = repository.autoBookForDate("2026-10-10", listOf("BREAKFAST", "DINNER"))

        assertEquals(1, result.bookedCount)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun testVerifyAndCatchUpBooking_allBooked_doesNotRebook() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_tok","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(16L, 0L)

        // Mock reservations: Breakfast, Lunch, Dinner already booked for 2026-10-10
        val resJson = """[
            {"id": 1, "date_reserve": "2026-10-10", "mealtype_fr": "Petit-déjeuner"},
            {"id": 2, "date_reserve": "2026-10-10", "mealtype_fr": "Déjeuner"},
            {"id": 3, "date_reserve": "2026-10-10", "mealtype_fr": "Dîner"}
        ]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(resJson))

        val prefs = UserPreferences(fakePrefs).apply {
            matricule = "user"
            password = "pass"
            preferredRestaurantId = 801L
        }
        val repository = MealsRepository(client, prefs)

        val result = repository.verifyAndCatchUpBooking("2026-10-10")
        assertEquals(0, result.bookedCount)
        assertEquals(3, result.skippedCount)
        assertEquals(0, result.errors.size)
    }

    @Test
    fun testVerifyAndCatchUpBooking_missingLunch_booksMissingLunch() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_tok","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(16L, 0L)

        // Mock reservations: Breakfast and Dinner booked, but Lunch missing (for verify check)
        val resJson = """[
            {"id": 1, "date_reserve": "2026-10-10", "mealtype_fr": "Petit-déjeuner"},
            {"id": 3, "date_reserve": "2026-10-10", "mealtype_fr": "Dîner"}
        ]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(resJson))

        // Mock reservations for autoBookForDate
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(resJson))

        // Mock depots
        val depotsJson = """{
            "depots": [
                {"id": 101, "nameFR": "RU Central", "nameAR": "مركزي", "isRu": 1, "breakfast": true, "lunch": true, "dinner": true}
            ]
        }"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(depotsJson))

        // Mock book response for missing lunch
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))

        val prefs = UserPreferences(fakePrefs).apply {
            matricule = "user"
            password = "pass"
            preferredRestaurantId = 101L
        }
        val repository = MealsRepository(client, prefs)

        val result = repository.verifyAndCatchUpBooking("2026-10-10")
        assertEquals(1, result.bookedCount)
        assertEquals(0, result.errors.size)
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)

    override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val data: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) temp[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null && values != null) temp[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) data.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            data.putAll(temp)
        }
    }
}
