package com.example.webtumeals.data.network

import com.example.webtumeals.data.logging.DiagnosticLogger
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

                DiagnosticLogger.d(TAG, "Attempting WebEtu login at $url for user: $username")
                httpClient.newCall(request).execute().use { response ->
                    val respBody = response.body?.string().orEmpty()
                    DiagnosticLogger.logHttp("POST", url, response.code, respBody)

                    if (response.isSuccessful) {
                        val json = JSONObject(respBody)
                        val s = AuthSession(
                            token = json.optString("token"),
                            uuid = json.optString("uuid"),
                            userName = json.optString("userName", username)
                        )
                        session = s
                        DiagnosticLogger.i(TAG, "Login successful for user: $username (uuid: ${s.uuid})")
                        return@withContext s
                    } else if (response.code in listOf(401, 403)) {
                        val err = IllegalArgumentException("Identifiants incorrects. Vérifiez votre matricule et mot de passe.")
                        DiagnosticLogger.w(TAG, "Authentication rejected for $username (HTTP ${response.code})")
                        throw err
                    }
                }
            } catch (e: Exception) {
                lastException = e
                DiagnosticLogger.w(TAG, "Login attempt failed at $url: ${e.message}")
                if (e is IllegalArgumentException) throw e
            }
        }
        DiagnosticLogger.e(TAG, "All WebEtu login endpoints failed", lastException)
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

        DiagnosticLogger.d(TAG, "Fetching student profile from $url")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("GET", url, response.code, text)

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

        DiagnosticLogger.d(TAG, "Fetching student DIA cards from $url")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("GET", url, response.code, text)

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
                val anneeId = if (obj.has("anneeAcademiqueId")) {
                    obj.optLong("anneeAcademiqueId")
                } else if (obj.has("idAnneeAcademique")) {
                    obj.optLong("idAnneeAcademique")
                } else {
                    null
                }
                val wilaya = when {
                    obj.has("idWilaya") -> obj.optLong("idWilaya")
                    obj.has("wilayaId") -> obj.optLong("wilayaId")
                    obj.has("refCodeWilaya") -> obj.optLong("refCodeWilaya")
                    obj.has("id_wilaya") -> obj.optLong("id_wilaya")
                    else -> null
                }?.takeIf { it > 0 }

                val etab = when {
                    obj.has("idEtablissement") -> obj.optLong("idEtablissement")
                    obj.has("etablissementId") -> obj.optLong("etablissementId")
                    else -> null
                }?.takeIf { it > 0 }

                list.add(StudentCard(cardId, anneeId, wilaya, etab))
            }
            DiagnosticLogger.i(TAG, "Retrieved ${list.size} DIA card(s): $list")
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

        DiagnosticLogger.d(TAG, "Fetching current academic year from $url")
        httpClient.newCall(request).execute().use { response ->
            DiagnosticLogger.logHttp("GET", url, response.code, "")
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

    suspend fun getWilayaInscription(cardId: Long, fallbackCard: StudentCard? = null): Long = withContext(Dispatchers.IO) {
        val url = "$primaryUrl/infos/wilayaInscription/$cardId"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Authorization", getAuthHeader())
            .header("User-Agent", USER_AGENT)
            .build()

        DiagnosticLogger.d(TAG, "Resolving wilaya inscription for cardId=$cardId")
        try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty().trim()
                DiagnosticLogger.logHttp("GET", url, response.code, text)

                val parsedId = parseWilayaIdFromResponse(text)
                if (parsedId > 0) {
                    DiagnosticLogger.i(TAG, "Resolved wilaya ID $parsedId from endpoint for cardId=$cardId")
                    return@withContext parsedId
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Failed to fetch wilayaInscription from endpoint: ${e.message}")
        }

        // Fallback: Check DIA metadata or fallback card
        val fallbackWilaya = fallbackCard?.wilayaId ?: fallbackCard?.etablissementId ?: 0L
        DiagnosticLogger.i(TAG, "Using fallback wilaya ID: $fallbackWilaya for cardId=$cardId")
        fallbackWilaya
    }

    fun parseWilayaIdFromResponse(text: String): Long {
        if (text.isBlank()) return 0L
        return try {
            if (text.startsWith("{")) {
                val json = JSONObject(text)
                when {
                    json.has("id") && json.optLong("id") > 0 -> json.optLong("id")
                    json.has("idWilaya") && json.optLong("idWilaya") > 0 -> json.optLong("idWilaya")
                    json.has("wilayaId") && json.optLong("wilayaId") > 0 -> json.optLong("wilayaId")
                    json.has("refCodeWilaya") && json.optLong("refCodeWilaya") > 0 -> json.optLong("refCodeWilaya")
                    json.has("id_wilaya") && json.optLong("id_wilaya") > 0 -> json.optLong("id_wilaya")
                    json.has("wilaya_id") && json.optLong("wilaya_id") > 0 -> json.optLong("wilaya_id")
                    json.has("refWilayaId") && json.optLong("refWilayaId") > 0 -> json.optLong("refWilayaId")
                    json.optJSONObject("wilaya")?.has("id") == true -> json.getJSONObject("wilaya").optLong("id")
                    json.optJSONObject("refWilaya")?.has("id") == true -> json.getJSONObject("refWilaya").optLong("id")
                    else -> 0L
                }
            } else if (text.startsWith("[")) {
                val arr = JSONArray(text)
                if (arr.length() > 0) {
                    val first = arr.optJSONObject(0)
                    if (first != null) {
                        when {
                            first.has("id") && first.optLong("id") > 0 -> first.optLong("id")
                            first.has("idWilaya") && first.optLong("idWilaya") > 0 -> first.optLong("idWilaya")
                            first.has("wilayaId") && first.optLong("wilayaId") > 0 -> first.optLong("wilayaId")
                            first.has("refCodeWilaya") && first.optLong("refCodeWilaya") > 0 -> first.optLong("refCodeWilaya")
                            else -> 0L
                        }
                    } else {
                        arr.optLong(0)
                    }
                } else 0L
            } else {
                val clean = text.trim('"', ' ', '\n', '\r', '\t')
                clean.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error parsing wilaya response: ${e.message}")
            0L
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

        DiagnosticLogger.d(TAG, "Fetching housing accommodation records from $url")
        try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty().trim()
                DiagnosticLogger.logHttp("GET", url, response.code, text)

                if (text.isEmpty() || !response.isSuccessful) {
                    DiagnosticLogger.i(TAG, "No accommodation records or empty response. Using non-resident fallback 0L.")
                    return@withContext 0L
                }

                val array = if (text.startsWith("[")) {
                    JSONArray(text)
                } else if (text.startsWith("{")) {
                    JSONArray().put(JSONObject(text))
                } else {
                    JSONArray()
                }

                if (array.length() == 0) {
                    DiagnosticLogger.i(TAG, "demandesHebregement is empty (external student). Fallback to 0L.")
                    return@withContext 0L
                }

                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    val resId = extractResidenceIdFromRow(row)
                    if (resId > 0) {
                        val rYear = if (row.has("idAnneeAcademique")) row.optLong("idAnneeAcademique") else row.optLong("anneeAcademiqueId")
                        if (yearId == null || rYear == yearId || yearId == 0L || rYear == 0L) {
                            DiagnosticLogger.i(TAG, "Found matching approved residence ID: $resId for year: $yearId")
                            return@withContext resId
                        }
                    }
                }

                // Fallback to first row with a valid residence ID
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    val resId = extractResidenceIdFromRow(row)
                    if (resId > 0) {
                        DiagnosticLogger.i(TAG, "Found approved residence ID from first valid row: $resId")
                        return@withContext resId
                    }
                }

                DiagnosticLogger.i(TAG, "No specific residence found in accommodation records; using 0L.")
                return@withContext 0L
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Failed to getApprovedResidenceId (${e.message}), defaulting to 0L")
            0L
        }
    }

    private fun extractResidenceIdFromRow(row: JSONObject): Long {
        return when {
            row.has("idResidence") && row.optLong("idResidence") > 0 -> row.optLong("idResidence")
            row.has("idResidance") && row.optLong("idResidance") > 0 -> row.optLong("idResidance")
            row.has("residenceId") && row.optLong("residenceId") > 0 -> row.optLong("residenceId")
            row.has("id_residence") && row.optLong("id_residence") > 0 -> row.optLong("id_residence")
            row.has("id_residance") && row.optLong("id_residance") > 0 -> row.optLong("id_residance")
            row.has("refResidence") && row.optLong("refResidence") > 0 -> row.optLong("refResidence")
            row.has("refResidance") && row.optLong("refResidance") > 0 -> row.optLong("refResidance")
            else -> 0L
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

        DiagnosticLogger.d(TAG, "Initiating ONOU login exchange (wilaya=$wilayaId, residence=$residenceId)")
        httpClient.newCall(request).execute().use { response ->
            val respBody = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("POST", "$onouUrl/loginpwebetu", response.code, respBody)

            if (!response.isSuccessful) {
                val errorMsg = extractOnouErrorMessage(respBody, "Échec de l'échange ONOU (HTTP ${response.code})")
                DiagnosticLogger.e(TAG, "ONOU login failed: $errorMsg")
                throw RuntimeException(errorMsg)
            }
            val tok = if (respBody.startsWith("{")) {
                val json = JSONObject(respBody)
                json.optString("token")
            } else {
                respBody
            }
            if (tok.isBlank()) {
                val errorMsg = "ONOU n'a retourné aucun jeton de session."
                DiagnosticLogger.e(TAG, errorMsg)
                throw RuntimeException(errorMsg)
            }
            onouToken = tok
            onouContext = ctx
            DiagnosticLogger.i(TAG, "ONOU session acquired successfully")
            tok
        }
    }

    suspend fun loginOnouAuto(): String = withContext(Dispatchers.IO) {
        val cards = getStudentCards()
        if (cards.isEmpty()) throw RuntimeException("Aucune carte d'étudiant (DIA) trouvée.")

        val yearId = getCurrentAcademicYearId()
        // Select the most recent active DIA card (matching yearId, or highest academic year ID, or last card)
        val card = if (yearId != null) {
            cards.firstOrNull { it.anneeAcademiqueId == yearId }
                ?: cards.maxByOrNull { it.anneeAcademiqueId ?: 0L }
                ?: cards.maxByOrNull { it.cardId }
                ?: cards.last()
        } else {
            cards.maxByOrNull { it.anneeAcademiqueId ?: 0L }
                ?: cards.maxByOrNull { it.cardId }
                ?: cards.last()
        }
        DiagnosticLogger.i(TAG, "Selected active DIA card: cardId=${card.cardId}, anneeId=${card.anneeAcademiqueId}")

        val wilayaId = getWilayaInscription(card.cardId, card)
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

        DiagnosticLogger.d(TAG, "Fetching ONOU restaurants from $url")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("GET", url.toString(), response.code, text)

            if (!response.isSuccessful) {
                val err = extractOnouErrorMessage(text, "Impossible de charger les restaurants (HTTP ${response.code})")
                DiagnosticLogger.e(TAG, err)
                throw RuntimeException(err)
            }
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
            DiagnosticLogger.i(TAG, "Loaded ${list.size} restaurant depot(s)")
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

        DiagnosticLogger.d(TAG, "Fetching live meal reservations")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("GET", url.toString(), response.code, text)

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
            DiagnosticLogger.i(TAG, "Loaded ${list.size} reservation(s)")
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
            val details = JSONArray().put(detailJson.toString())
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

        DiagnosticLogger.d(TAG, "Booking meal: date=$dateStr, meal=$mealType (menu=$menuType), depot=$restaurantId")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("POST", "$onouUrl/reservemeal", response.code, text)

            if (response.code in listOf(200, 201)) {
                if (text.startsWith("{")) {
                    val json = JSONObject(text)
                    val dataArr = json.optJSONArray("data")
                    if (dataArr != null && dataArr.length() > 0) {
                        for (i in 0 until dataArr.length()) {
                            val item = dataArr.optJSONObject(i) ?: continue
                            if (!item.optBoolean("status", true)) {
                                val msg = extractOnouErrorMessage(item.toString(), "Échec de réservation")
                                DiagnosticLogger.w(TAG, "Reservation rejected: $msg")
                                throw RuntimeException(msg)
                            }
                        }
                    }
                    if (json.optBoolean("success", true)) {
                        DiagnosticLogger.i(TAG, "Successfully booked $mealType for $dateStr at depot $restaurantId")
                        return@withContext true
                    } else {
                        val msg = extractOnouErrorMessage(text, "Échec de réservation")
                        DiagnosticLogger.w(TAG, "Reservation rejected: $msg")
                        throw RuntimeException(msg)
                    }
                } else {
                    val msg = "Réponse inattendue du serveur ONOU (non-JSON)"
                    DiagnosticLogger.e(TAG, "$msg: ${text.take(150)}")
                    throw RuntimeException(msg)
                }
            }
            val formattedError = extractOnouErrorMessage(text, "Réservation refusée par l'ONOU (Code ${response.code})")
            DiagnosticLogger.e(TAG, "Meal booking error: $formattedError")
            throw RuntimeException(formattedError)
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

        DiagnosticLogger.d(TAG, "Cancelling meal reservation: id=$reservationId")
        httpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().trim()
            DiagnosticLogger.logHttp("DELETE", "$onouUrl/reservemeal/$reservationId", response.code, text)

            if (response.isSuccessful) {
                DiagnosticLogger.i(TAG, "Successfully cancelled meal reservation: $reservationId")
                return@withContext true
            }
            val formattedError = extractOnouErrorMessage(text, "Annulation impossible (HTTP ${response.code})")
            DiagnosticLogger.e(TAG, "Meal cancellation error: $formattedError")
            throw RuntimeException(formattedError)
        }
    }

    fun extractOnouErrorMessage(responseBody: String, defaultMsg: String): String {
        if (responseBody.isBlank()) return defaultMsg
        try {
            if (responseBody.startsWith("{")) {
                val json = JSONObject(responseBody)
                val dataArr = json.optJSONArray("data")
                if (dataArr != null && dataArr.length() > 0) {
                    val firstItem = dataArr.optJSONObject(0)
                    if (firstItem != null) {
                        val msg = firstItem.optString("message").ifBlank {
                            firstItem.optString("error").ifBlank {
                                firstItem.optString("msg")
                            }
                        }
                        if (msg.isNotBlank()) return formatOnouFriendlyMessage(msg)
                    }
                }
                val msg = json.optString("message").ifBlank {
                    json.optString("error").ifBlank {
                        json.optString("msg").ifBlank {
                            json.optString("libelle").ifBlank {
                                json.optString("description")
                            }
                        }
                    }
                }
                if (msg.isNotBlank()) return formatOnouFriendlyMessage(msg)
                val errorsArr = json.optJSONArray("errors")
                if (errorsArr != null && errorsArr.length() > 0) {
                    return formatOnouFriendlyMessage(errorsArr.optString(0))
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Failed to parse error body: ${e.message}")
        }
        return formatOnouFriendlyMessage(responseBody.take(150))
    }

    private fun formatOnouFriendlyMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            raw.contains("لا يمكن الحجز لليوم نفسه") -> "Impossible de réserver pour la date d'aujourd'hui (délai dépassé)"
            raw.contains("لا توجد حجوزات صالحة") -> "Aucune réservation valide pour ce créneau"
            raw.contains("رصيد غير كاف") || (lower.contains("solde") && (lower.contains("insuffisant") || lower.contains("epuise") || lower.contains("épuisé"))) -> "Solde insuffisant pour réserver ce repas"
            raw.contains("تم الحجز مسبقا") || lower.contains("deja") || lower.contains("déjà") || lower.contains("already") -> "Repas déjà réservé pour ce créneau"
            lower.contains("ferme") || lower.contains("fermé") || lower.contains("delai") || lower.contains("délai") || lower.contains("depasse") || lower.contains("dépassé") -> "Réservation fermée ou délai dépassé pour cette date"
            else -> raw
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
        private const val TAG = "WebEtuClient"
        private const val USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Build/UP1A.231005.007)"
        private const val ONOU_SIGNATURE_KEY = "pUzHUW2WX54uCzhO8JC2eQ6g1Ol21upw"
    }
}
