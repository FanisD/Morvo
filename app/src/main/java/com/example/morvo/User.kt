package com.example.morvo

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val firstName: String = "",
    val age: Int = 0,
    val gender: String = "",
    val interestedIn: String = "",
    val city: String = "",
    val occupation: String = "",
    val imageUrls: List<String> = emptyList(),
    val profileAnswers: Map<String, Any> = emptyMap(),
    val email: String = "",
    val currentVenueId: String? = null,
    val lastLat: Double = 0.0,
    val lastLng: Double = 0.0,
    val availableUntil: Long = 0L,
    val blockedUsers: List<String> = emptyList()
)