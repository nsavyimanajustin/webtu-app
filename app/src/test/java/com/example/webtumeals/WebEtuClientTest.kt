package com.example.webtumeals

import com.example.webtumeals.data.model.StudentCard
import com.example.webtumeals.data.network.WebEtuClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
        // Authenticate first
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        // Server returns raw string/integer "34" (BBA wilaya code) instead of {"id": 34}
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("34\n"))

        val wilayaId = client.getWilayaInscription(1001L)
        assertEquals(34L, wilayaId)
    }

    @Test
    fun testGetWilayaInscription_jsonObject() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"id": 34, "libelle": "Bordj Bou Arreridj"}"""))

        val wilayaId = client.getWilayaInscription(1001L)
        assertEquals(34L, wilayaId)
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
    fun testGetStudentCards_array() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        val cardsJson = """[{"id": 9999, "anneeAcademiqueId": 42}, {"id": 8888, "idAnneeAcademique": 41}]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(cardsJson))

        val cards = client.getStudentCards()
        assertEquals(2, cards.size)
        assertEquals(9999L, cards[0].cardId)
        assertEquals(42L, cards[0].anneeAcademiqueId)
        assertEquals(8888L, cards[1].cardId)
        assertEquals(41L, cards[1].anneeAcademiqueId)
    }

    @Test
    fun testGetApprovedResidenceId_array() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"mock_token","uuid":"u1"}"""))
        client.login("user", "pass")

        val housingJson = """[{"id": 1, "idResidance": 505, "idAnneeAcademique": 42}]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(housingJson))

        val residenceId = client.getApprovedResidenceId(42L)
        assertEquals(505L, residenceId)
    }

    @Test
    fun testLoginOnouAuto_fullFlow() = runTest {
        // 1. login
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")

        // 2. getStudentCards
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"id": 1234, "anneeAcademiqueId": 42}]"""))

        // 3. getCurrentAcademicYearId
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("42"))

        // 4. getWilayaInscription -> returns "34"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("34"))

        // 5. getApprovedResidenceId -> returns [{"idResidance": 777, "idAnneeAcademique": 42}]
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""[{"idResidance": 777, "idAnneeAcademique": 42}]"""))

        // 6. loginpwebetu
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_session_token_xyz"}"""))

        val onouToken = client.loginOnouAuto()
        assertEquals("onou_session_token_xyz", onouToken)
    }

    @Test
    fun testBookMeal_detailsPayload() = runTest {
        // Prepare login & onou login
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(34L, 777L)

        // Mock book response
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))

        val success = client.bookMeal("2026-10-02", "LUNCH", 3L)
        assertTrue(success)

        val recordedRequest = mockServer.takeRequest() // login
        mockServer.takeRequest() // onou login
        val bookRequest = mockServer.takeRequest() // bookMeal

        val body = bookRequest.body.readUtf8()
        // Ensure details contains JSON object array [ { ... } ], not stringified array [ "..." ]
        assertTrue(body.contains("\"details\":[{\""))
        assertTrue(body.contains("\"idDepot\":3"))
        assertTrue(body.contains("\"menu_type\":2"))
    }

    @Test
    fun testBookMeal_breakfast_menuType1() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(34L, 777L)

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        val success = client.bookMeal("2026-10-03", "BREAKFAST", 405L)
        assertTrue(success)

        mockServer.takeRequest() // login
        mockServer.takeRequest() // onou login
        val bookRequest = mockServer.takeRequest()
        val body = bookRequest.body.readUtf8()
        assertTrue(body.contains("\"menu_type\":1"))
        assertTrue(body.contains("\"idDepot\":405"))
    }

    @Test
    fun testBookMeal_dinner_menuType3() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(34L, 777L)

        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true}"""))
        val success = client.bookMeal("2026-10-03", "DINNER", 405L)
        assertTrue(success)

        mockServer.takeRequest() // login
        mockServer.takeRequest() // onou login
        val bookRequest = mockServer.takeRequest()
        val body = bookRequest.body.readUtf8()
        assertTrue(body.contains("\"menu_type\":3"))
        assertTrue(body.contains("\"idDepot\":405"))
    }

    @Test
    fun testGetOnouDepots_parsesFlags() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(34L, 777L)

        val depotsJson = """{
            "depots": [
                {"id": 405, "nameFR": "RU Nasri Fatoum", "nameAR": "اقامة", "isRu": 1, "breakfast": true, "lunch": true, "dinner": true},
                {"id": 548, "nameFR": "Resto Central", "nameAR": "مركزي", "isRu": 0, "breakfast": false, "lunch": true, "dinner": false}
            ]
        }"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(depotsJson))

        val depots = client.getOnouDepots()
        assertEquals(2, depots.size)
        assertTrue(depots[0].isRu)
        assertTrue(depots[0].servesBreakfast)
        assertTrue(!depots[1].isRu)
        assertTrue(!depots[1].servesBreakfast)
    }

    @Test
    fun testGetStudentReservations_parsesBreakfastAndCanDelete() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"tok1","uuid":"u1"}"""))
        client.login("user", "pass")
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"token":"onou_tok"}"""))
        client.loginOnou(34L, 777L)

        val resJson = """[
            {"id": 100, "date_reserve": "2026-10-02", "mealtype_fr": "Petit-déjeuner", "candelete": true},
            {"id": 101, "date_reserve": "2026-10-02", "mealtype_fr": "Dîner", "candelete": false}
        ]"""
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody(resJson))

        val list = client.getStudentReservations()
        assertEquals(2, list.size)
        assertEquals("BREAKFAST", list[0].mealType)
        assertTrue(list[0].canDelete)
        assertEquals("DINNER", list[1].mealType)
        assertTrue(!list[1].canDelete)
    }
}
