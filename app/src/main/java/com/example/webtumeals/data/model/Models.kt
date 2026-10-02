package com.example.webtumeals.data.model

data class AuthSession(
    val token: String,
    val uuid: String,
    val userName: String
)

data class StudentProfile(
    val uuid: String,
    val nomLatin: String?,
    val prenomLatin: String?,
    val nomArabe: String?,
    val prenomArabe: String?
) {
    val fullName: String
        get() = listOfNotNull(prenomLatin, nomLatin).joinToString(" ").ifBlank {
            listOfNotNull(prenomArabe, nomArabe).joinToString(" ").ifBlank { uuid }
        }
}

data class StudentCard(
    val cardId: Long,
    val anneeAcademiqueId: Long?
)

data class RestaurantDepot(
    val id: Long,
    val nameFR: String,
    val nameAR: String,
    val isRu: Boolean = true,
    val servesBreakfast: Boolean = true,
    val servesLunch: Boolean = true,
    val servesDinner: Boolean = true
) {
    val displayName: String
        get() = nameFR.ifBlank { nameAR }

    fun localizedName(lang: String): String = when (lang) {
        "AR" -> nameAR.ifBlank { nameFR }
        else -> nameFR.ifBlank { nameAR }
    }
}

data class MealReservation(
    val id: Long?,
    val date: String,
    val mealType: String, // "BREAKFAST", "LUNCH", "DINNER"
    val mealLabel: String,
    val restaurantId: Long?,
    val restaurantName: String?,
    val isReserved: Boolean,
    val ticketCode: String?,
    val status: String,
    val canDelete: Boolean = false
)
