package com.example.webtumeals

import com.example.webtumeals.data.model.StudentCard
import com.example.webtumeals.data.network.WebEtuClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebEtuClientTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var client: WebEtuClient
    private lateinit var baseUrl: String

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
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun testLogin_successful() = runTest {
        val loginResponse = """{"token":"Bearer mock_token_123","uuid":"mock_uuid_456","userName":"202034012345"}"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(loginResponse))

        val session = client.login("202034012345", "password123")
        assertEquals("Bearer mock_token_123", session.token)
        assertEquals("mock_uuid_456", session.uuid)
        assertEquals("202034012345", session.userName)
    }

    @Test
    fun testGetWilayaInscription_rawInteger_doesNotCrash() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // Server returns raw string/integer "16" (Algiers)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("16\n"))

        val wilayaId = client.getWilayaInscription(1001L)
        assertEquals(16L, wilayaId)
    }

    @Test
    fun testGetWilayaInscription_keyVariations() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // Test idWilaya
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"idWilaya": 31, "libelle": "Oran"}"""))
        assertEquals(31L, client.getWilayaInscription(1001L))

        // Test wilayaId
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"wilayaId": 25, "libelle": "Constantine"}"""))
        assertEquals(25L, client.getWilayaInscription(1002L))

        // Test refCodeWilaya
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"refCodeWilaya": 19, "libelle": "Setif"}"""))
        assertEquals(19L, client.getWilayaInscription(1003L))

        // Test nested refWilaya
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"refWilaya": {"id": 15, "libelle": "Tizi Ouzou"}}"""))
        assertEquals(15L, client.getWilayaInscription(1004L))
    }

    @Test
    fun testGetWilayaInscription_fallbackFromDiaCard() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // Endpoint returns 404 or empty
        mockServer.enqueue(MockResponse().setResponseCode(404).setBody(""))

        val diaCard = StudentCard(cardId = 999L, anneeAcademiqueId = 42L, wilayaId = 6L)
        val wilayaId = client.getWilayaInscription(999L, diaCard)
        assertEquals(6L, wilayaId)
    }

    @Test
    fun testGetCurrentAcademicYearId_rawInteger() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("42\n"))

        val yearId = client.getCurrentAcademicYearId()
        assertEquals(42L, yearId)
    }

    @Test
    fun testGetCurrentAcademicYearId_jsonObject() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"idAnneeAcademique": 42, "code": "2024-2025"}"""))

        val yearId = client.getCurrentAcademicYearId()
        assertEquals(42L, yearId)
    }

    @Test
    fun testGetStudentCards_arrayAndWilayaExtraction() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        val cardsJson = """[
            {"id": 8888, "idAnneeAcademique": 41, "idWilaya": 34},
            {"id": 9999, "anneeAcademiqueId": 42, "refCodeWilaya": 31, "idEtablissement": 105}
        ]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(cardsJson))

        val cards = client.getStudentCards()
        assertEquals(2, cards.size)
        assertEquals(8888L, cards[0].cardId)
        assertEquals(41L, cards[0].anneeAcademiqueId)
        assertEquals(34L, cards[0].wilayaId)

        assertEquals(9999L, cards[1].cardId)
        assertEquals(42L, cards[1].anneeAcademiqueId)
        assertEquals(31L, cards[1].wilayaId)
        assertEquals(105L, cards[1].etablissementId)
    }

    @Test
    fun testGetApprovedResidenceId_variations() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // idResidance (with a)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id": 1, "idResidance": 505, "idAnneeAcademique": 42}]"""))
        assertEquals(505L, client.getApprovedResidenceId(42L))

        // idResidence (with e)
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id": 2, "idResidence": 606, "anneeAcademiqueId": 43}]"""))
        assertEquals(606L, client.getApprovedResidenceId(43L))

        // residenceId
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id": 3, "residenceId": 707}]"""))
        assertEquals(707L, client.getApprovedResidenceId(null))
    }

    @Test
    fun testGetApprovedResidenceId_emptyArray_returnsZeroGracefully() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // External student with no housing requests
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        val resId = client.getApprovedResidenceId(42L)
        assertEquals(0L, resId)
    }

    @Test
    fun testLoginOnouAuto_selectsLatestCard() = runTest {
        // 1. login
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")

        // 2. getStudentCards - oldest card first, newest card second
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[
            {"id": 1001, "anneeAcademiqueId": 40},
            {"id": 2002, "anneeAcademiqueId": 42}
        ]"""))

        // 3. getCurrentAcademicYearId
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("42"))

        // 4. getWilayaInscription -> returns "16"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"idWilaya": 16}"""))

        // 5. getApprovedResidenceId -> returns [{"idResidence": 777, "idAnneeAcademique": 42}]
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"idResidence": 777, "idAnneeAcademique": 42}]"""))

        // 6. loginpwebetu
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_session_token_xyz"}"""))

        val onouToken = client.loginOnouAuto()
        assertEquals("onou_session_token_xyz", onouToken)
    }

    @Test
    fun testBookMeal_payloadIsStringifiedAndSuccessful() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(16L, 777L)

        val successJson = """{"success": true, "data": [{"date": "2026-10-10", "meal": "غداء", "status": true, "message": "تم الحجز بنجاح"}]}"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(successJson))

        val result = client.bookMeal("2026-10-10", "LUNCH", 10L)
        assertTrue(result)

        // Drain login requests
        mockServer.takeRequest() // login
        mockServer.takeRequest() // loginpwebetu
        val bookingRequest = mockServer.takeRequest()
        val requestBody = bookingRequest.body.readUtf8()
        val json = JSONObject(requestBody)
        val detailsArr = json.getJSONArray("details")
        assertEquals(1, detailsArr.length())
        val firstDetail = detailsArr.get(0)
        assertTrue("Detail element MUST be a JSON String, not a JSONObject", firstDetail is String)
        val innerJson = JSONObject(firstDetail as String)
        assertEquals("2026-10-10", innerJson.getString("date_reserve"))
        assertEquals(2, innerJson.getInt("menu_type"))
        assertEquals(10L, innerJson.getLong("idDepot"))
    }

    @Test
    fun testBookMeal_insufficientBalanceError() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(16L, 777L)

        val errJson = """{"success": false, "data": [{"status": false, "message": "Solde insuffisant pour réserver le repas"}]}"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(errJson))

        try {
            client.bookMeal("2026-10-10", "LUNCH", 10L)
            assertTrue("Should have thrown exception", false)
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Solde insuffisant") == true)
        }
    }

    @Test
    fun testExtractOnouErrorMessage_helpers() {
        val json1 = """{"message": "Délai dépassé pour la réservation"}"""
        val msg1 = client.extractOnouErrorMessage(json1, "Default")
        assertTrue(msg1.contains("Réservation fermée ou délai dépassé"))

        val json2 = """{"data": [{"status": false, "message": "Votre solde est épuisé"}]}"""
        val msg2 = client.extractOnouErrorMessage(json2, "Default")
        assertTrue(msg2.contains("Solde insuffisant"))
    }
}
