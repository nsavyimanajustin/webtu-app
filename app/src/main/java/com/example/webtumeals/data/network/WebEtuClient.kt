package com.example.webtumeals.data.network

import com.example.webtumeals.data.model.AuthSession
import com.example.webtumeals.data.model.MealReservation
import com.example.webtumeals.data.model.RestaurantDepot
import com.example.webtumeals.data.model.StudentCard
import com.example.webtumeals.data.model.StudentProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class WebEtuClient(
    private val primaryUrl: String = "https://api-webetu.mesrs.dz/api",
    private val fallbackUrl: String = "https://progres.mesrs.dz/api",
    private val onouUrl: String = "https://gs-api.onou.dz/api"
) {
    private var session: AuthSession? = null
    private var onouToken: String? = null
    private var onouContext: JSONObject? = null

    private val httpClient: OkHttpClient by lazy {
        createUnsafeOkHttpClient()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val currentSession: AuthSession?
        get() = session

    private fun getAuthHeader(): String {
        val token = session?.token ?: throw IllegalStateException("Non authentifié. Connectez-vous d'abord.")
        return token.trim().removePrefix("Bearer ")
    }

    private fun computeHmacSha256(key: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    suspend fun login(username: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("username", username.trim())
            put("password", password.trim())
        }
        val body = payload.toString().toRequestBody(jsonMediaType)

        val urls = listOf("$primaryUrl/authentication/v1/", "$fallbackUrl/authentication/v1/")
        var lastException: Exception? = null

        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(respBody)
                        val s = AuthSession(
                            token = json.optString("token"),
                            uuid = json.optString("uuid"),
                            userName = json.optString("userName", username)
                        )
                        session = s
                        return@withContext s
                    } else if (response.code in listOf(401, 403)) {
                        throw IllegalArgumentException("Identifiants incorrects. Vérifiez votre matricule et mot de passe.")
                    }
                }
            } catch (e: Exception) {
                lastException = e
                if (e is IllegalArgumentException) throw e
            }
        }
        throw lastException ?: RuntimeException("Échec de connexion au serveur WebEtu.")
    }

    suspend fun getStudentProfile(): StudentProfile = withContext(Dispatchers.IO) {
        val s = session ?: throw IllegalStateException("Session requise")
        val url = "$primaryUrl/infos/bac/${s.uuid}/individu"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                StudentProfile(
                    uuid = s.uuid,
                    nomLatin = json.optString("nomLatin"),
                    prenomLatin = json.optString("prenomLatin"),
                    nomArabe = json.optString("nomArabe"),
                    prenomArabe = json.optString("prenomArabe")
                )
            } else {
                StudentProfile(s.uuid, "", "", "", "")
            }
        }
    }

    suspend fun getStudentCards(): List<StudentCard> = withContext(Dispatchers.IO) {
        val s = session ?: throw IllegalStateException("Session requise")
        val url = "$primaryUrl/infos/bac/${s.uuid}/dias"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            val array = if (text.startsWith("[")) {
                JSONArray(text)
            } else if (text.startsWith("{")) {
                JSONArray().put(JSONObject(text))
            } else {
                JSONArray()
            }
            val list = mutableListOf<StudentCard>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val cardId = obj.optLong("id")
                val anneeId = if (obj.has("anneeAcademiqueId")) obj.optLong("anneeAcademiqueId") else obj.optLong("idAnneeAcademique")
                list.add(StudentCard(cardId, anneeId))
            }
            list
        }
    }

    suspend fun getCurrentAcademicYearId(): Long? = withContext(Dispatchers.IO) {
        val url = "$primaryUrl/infos/AnneeAcademiqueEncours"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val text = response.body?.string().orEmpty().trim()
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                if (json.has("idAnneeAcademique")) json.optLong("idAnneeAcademique") else json.optLong("id")
            } else {
                val clean = text.trim('"', ' ', '\n', '\r', '\t')
                clean.toLongOrNull()
            }
        }
    }

    suspend fun getWilayaInscription(cardId: Long): Long = withContext(Dispatchers.IO) {
        val url = "$primaryUrl/infos/wilayaInscription/$cardId"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                val id = if (json.has("id")) json.optLong("id") else json.optLong("wilayaId")
                if (id > 0) id else 34L
            } else {
                val clean = text.trim('"', ' ', '\n', '\r', '\t')
                clean.toLongOrNull() ?: 34L
            }
        }
    }

    suspend fun getApprovedResidenceId(yearId: Long?): Long = withContext(Dispatchers.IO) {
        val s = session ?: throw IllegalStateException("Session requise")
        val url = "$primaryUrl/infos/bac/${s.uuid}/demandesHebregement"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (text.isEmpty()) throw RuntimeException("Aucun enregistrement d'hébergement reçu.")
            val array = if (text.startsWith("[")) {
                JSONArray(text)
            } else if (text.startsWith("{")) {
                JSONArray().put(JSONObject(text))
            } else {
                JSONArray()
            }
            for (i in 0 until array.length()) {
                val row = array.optJSONObject(i) ?: continue
                if (row.has("idResidance")) {
                    val rYear = row.optLong("idAnneeAcademique")
                    if (yearId == null || rYear == yearId || yearId == 0L) {
                        return@withContext row.getLong("idResidance")
                    }
                }
            }
            if (array.length() > 0 && array.optJSONObject(0)?.has("idResidance") == true) {
                return@withContext array.getJSONObject(0).getLong("idResidance")
            }
            throw RuntimeException("Aucun enregistrement de résidence trouvé pour cet étudiant.")
        }
    }

    suspend fun loginOnou(wilayaId: Long, residenceId: Long): String = withContext(Dispatchers.IO) {
        val s = session ?: throw IllegalStateException("Session WebEtu requise")
        val ctx = JSONObject().apply {
            put("uuid", s.uuid)
            put("wilaya", wilayaId)
            put("residence", residenceId)
            put("token", getAuthHeader())
        }

        val bodyStr = ctx.toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = computeHmacSha256(ONOU_SIGNATURE_KEY, "$timestamp|$nonce|$bodyStr")

        val request = Request.Builder()
            .url("$onouUrl/loginpwebetu")
            .post(bodyStr.toRequestBody(jsonMediaType))
            .header("Authorization", getAuthHeader())
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val respBody = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw RuntimeException("Échec de l'échange ONOU (HTTP ${response.code}): $respBody")
            }
            val tok = if (respBody.startsWith("{")) {
                val json = JSONObject(respBody)
                json.optString("token")
            } else {
                respBody
            }
            if (tok.isBlank()) throw RuntimeException("ONOU n'a retourné aucun jeton de session.")
            onouToken = tok
            onouContext = ctx
            tok
        }
    }

    suspend fun loginOnouAuto(): String = withContext(Dispatchers.IO) {
        val cards = getStudentCards()
        if (cards.isEmpty()) throw RuntimeException("Aucune carte d'étudiant (DIA) trouvée.")
        val yearId = getCurrentAcademicYearId()
        val card = if (yearId != null) {
            cards.firstOrNull { it.anneeAcademiqueId == yearId } ?: cards.first()
        } else {
            cards.first()
        }
        val wilayaId = getWilayaInscription(card.cardId)
        val residenceId = getApprovedResidenceId(yearId)
        loginOnou(wilayaId, residenceId)
    }

    suspend fun getOnouDepots(): List<RestaurantDepot> = withContext(Dispatchers.IO) {
        val ctx = onouContext ?: throw IllegalStateException("Contexte ONOU non initialisé.")
        val tok = onouToken ?: throw IllegalStateException("Token ONOU non initialisé.")

        val urlBuilder = "$onouUrl/getdepotres".toHttpUrlOrNull()!!.newBuilder()
        val keys = ctx.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            urlBuilder.addQueryParameter(key, ctx.optString(key))
        }
        val url = urlBuilder.build()

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = computeHmacSha256(ONOU_SIGNATURE_KEY, "$timestamp|$nonce|")

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $tok")
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) throw RuntimeException("Impossible de charger les restaurants (HTTP ${response.code}): $text")
            val depotsArray = if (text.startsWith("[")) {
                JSONArray(text)
            } else if (text.startsWith("{")) {
                val json = JSONObject(text)
                json.optJSONArray("depots") ?: json.optJSONArray("data") ?: JSONArray()
            } else {
                JSONArray()
            }
            val list = mutableListOf<RestaurantDepot>()
            for (i in 0 until depotsArray.length()) {
                val item = depotsArray.optJSONObject(i) ?: continue
                val id = item.optLong("id")
                val fr = item.optString("nameFR", item.optString("designationFr", ""))
                val ar = item.optString("nameAR", item.optString("designationAr", ""))
                val isRu = if (item.has("isRu")) item.optInt("isRu", 1) == 1 else true
                val breakfast = item.optBoolean("breakfast", true)
                val lunch = item.optBoolean("lunch", true)
                val dinner = item.optBoolean("dinner", true)
                list.add(RestaurantDepot(id, fr, ar, isRu, breakfast, lunch, dinner))
            }
            list
        }
    }

    suspend fun getStudentReservations(): List<MealReservation> = withContext(Dispatchers.IO) {
        val ctx = onouContext ?: throw IllegalStateException("Contexte ONOU non initialisé.")
        val tok = onouToken ?: throw IllegalStateException("Token ONOU non initialisé.")

        val urlBuilder = "$onouUrl/meal-reservations/student".toHttpUrlOrNull()!!.newBuilder()
        val keys = ctx.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            urlBuilder.addQueryParameter(key, ctx.optString(key))
        }
        val url = urlBuilder.build()

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = computeHmacSha256(ONOU_SIGNATURE_KEY, "$timestamp|$nonce|")

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $tok")
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) return@withContext emptyList()
            val list = mutableListOf<MealReservation>()
            val rawArray: JSONArray = try {
                if (text.startsWith("[")) {
                    JSONArray(text)
                } else if (text.startsWith("{")) {
                    val root = JSONObject(text)
                    root.optJSONArray("data") ?: root.optJSONArray("reservations") ?: JSONArray()
                } else {
                    JSONArray()
                }
            } catch (e: Exception) {
                JSONArray()
            }

            for (i in 0 until rawArray.length()) {
                val item = rawArray.optJSONObject(i) ?: continue
                val mLabel = item.optString("mealtype_fr", item.optString("mealLabel", "Repas"))
                val mType = when {
                    mLabel.contains("Petit", ignoreCase = true) || mLabel.contains("Ftour", ignoreCase = true) || mLabel.contains("فطور", ignoreCase = true) || mLabel.contains("Breakfast", ignoreCase = true) -> "BREAKFAST"
                    mLabel.contains("Dîner", ignoreCase = true) || mLabel.contains("Diner", ignoreCase = true) || mLabel.contains("Dinner", ignoreCase = true) || mLabel.contains("عشاء", ignoreCase = true) -> "DINNER"
                    mLabel.contains("Déjeuner", ignoreCase = true) || mLabel.contains("Dejeuner", ignoreCase = true) || mLabel.contains("Lunch", ignoreCase = true) || mLabel.contains("غداء", ignoreCase = true) -> "LUNCH"
                    else -> "LUNCH"
                }
                val canDelete = item.optBoolean("candelete", false)
                list.add(
                    MealReservation(
                        id = if (item.has("id")) item.optLong("id") else null,
                        date = item.optString("date_reserve", item.optString("date")),
                        mealType = mType,
                        mealLabel = mLabel,
                        restaurantId = if (item.has("idDepot")) item.optLong("idDepot") else null,
                        restaurantName = item.optString("depot_fr", item.optString("depot_ar")),
                        isReserved = true,
                        ticketCode = item.optString("ticket_code", item.optString("code")),
                        status = item.optString("status", "CONFIRMED"),
                        canDelete = canDelete
                    )
                )
            }
            list
        }
    }

    suspend fun bookMeal(dateStr: String, mealType: String, restaurantId: Long): Boolean = withContext(Dispatchers.IO) {
        val tok = onouToken ?: throw IllegalStateException("Token ONOU non initialisé.")
        val menuType = when (mealType.uppercase()) {
            "BREAKFAST" -> 1
            "LUNCH" -> 2
            "DINNER" -> 3
            else -> 2
        }

        val detailJson = JSONObject().apply {
            put("date_reserve", dateStr)
            put("menu_type", menuType)
            put("idDepot", restaurantId)
        }

        val payload = JSONObject().apply {
            val details = JSONArray().put(detailJson)
            put("details", details)
        }

        val bodyStr = payload.toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = computeHmacSha256(ONOU_SIGNATURE_KEY, "$timestamp|$nonce|$bodyStr")

        val request = Request.Builder()
            .url("$onouUrl/reservemeal")
            .post(bodyStr.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $tok")
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            if (response.code in listOf(200, 201)) {
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    if (json.optBoolean("success", true)) {
                        return@withContext true
                    }
                } else {
                    return@withContext true
                }
            }
            throw RuntimeException("Réservation refusée par l'ONOU (Code ${response.code}): $text")
        }
    }

    suspend fun cancelMeal(reservationId: Long): Boolean = withContext(Dispatchers.IO) {
        val ctx = onouContext ?: JSONObject()
        val tok = onouToken ?: throw IllegalStateException("Token ONOU non initialisé.")

        val bodyStr = ctx.toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val nonce = UUID.randomUUID().toString()
        val signature = computeHmacSha256(ONOU_SIGNATURE_KEY, "$timestamp|$nonce|$bodyStr")

        val request = Request.Builder()
            .url("$onouUrl/reservemeal/$reservationId")
            .delete(bodyStr.toRequestBody(jsonMediaType))
            .header("Authorization", "Bearer $tok")
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Signature", signature)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) return@withContext true
            throw RuntimeException("Annulation impossible (HTTP ${response.code})")
        }
    }

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    companion object {
        private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Build/UP1A.231005.007)"
        private const val ONOU_SIGNATURE_KEY = "pUzHUW2WX54uCzhO8JC2eQ6g1Ol21upw"
    }
}
