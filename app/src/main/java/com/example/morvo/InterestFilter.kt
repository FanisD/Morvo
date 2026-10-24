package com.example.morvo

object InterestFilter {
    fun matches(
        myGender: String,
        myPreference: String,
        theirGender: String,
        theirPreference: String
    ): Boolean {
        fun matchesPreference(preference: String, gender: String): Boolean {
            if (preference.equals("Everyone", ignoreCase = true)) return true
            if (preference.equals("Men", ignoreCase = true) && gender.equals("Male", ignoreCase = true)) return true
            if (preference.equals("Women", ignoreCase = true) && gender.equals("Female", ignoreCase = true)) return true
            return preference.equals(gender, ignoreCase = true)
        }

        return matchesPreference(myPreference, theirGender) &&
            matchesPreference(theirPreference, myGender)
    }
}
