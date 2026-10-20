package com.example.morvo

import com.google.firebase.firestore.DocumentSnapshot

fun User.withProfileAnswers(doc: DocumentSnapshot): User {
    val raw = doc.get("profileAnswers") as? Map<*, *> ?: return this
    val answers = raw.entries.mapNotNull { (key, value) ->
        if (key == null || value == null) null else key.toString() to value
    }.toMap()
    return copy(profileAnswers = answers)
}

object ProfileAnswers {
    fun text(answers: Map<String, Any>, key: String): String? {
        val value = answers[key] as? String
        return value?.takeIf { it.isNotBlank() }
    }

    fun texts(answers: Map<String, Any>, key: String): List<String> {
        val value = answers[key] as? List<*> ?: return emptyList()
        return value.mapNotNull { it as? String }
    }

    fun sliders(answers: Map<String, Any>, key: String): Map<String, Int> {
        val value = answers[key] as? Map<*, *> ?: return emptyMap()
        return value.entries.mapNotNull { (sliderKey, sliderValue) ->
            val number = when (sliderValue) {
                is Int -> sliderValue
                is Long -> sliderValue.toInt()
                is Double -> sliderValue.toInt()
                else -> return@mapNotNull null
            }
            sliderKey?.toString()?.let { it to number }
        }.toMap()
    }
}
