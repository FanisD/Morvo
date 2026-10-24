package com.example.morvo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityCalculatorTest {

    @Test
    fun missingAnswersScoreZero() {
        val filled = User(profileAnswers = sampleAnswers())
        assertEquals(0, CompatibilityCalculator.percent(User(), filled))
        assertEquals(0, CompatibilityCalculator.percent(filled, User()))
    }

    @Test
    fun identicalAnswersScoreHigh() {
        val person = User(profileAnswers = sampleAnswers())
        assertTrue(CompatibilityCalculator.percent(person, person) >= 90)
    }

    @Test
    fun kidsYesVersusNoIsCapped() {
        val yes = User(profileAnswers = sampleAnswers(kids = "yes"))
        val no = User(profileAnswers = sampleAnswers(kids = "no"))
        assertTrue(CompatibilityCalculator.percent(yes, no) <= 45)
    }

    @Test
    fun petRefusalVersusOwnerIsCapped() {
        val owner = User(profileAnswers = sampleAnswers(hasPet = "yes", datePet = "yes"))
        val refuses = User(profileAnswers = sampleAnswers(hasPet = "no", datePet = "no"))
        assertTrue(CompatibilityCalculator.percent(owner, refuses) <= 55)
    }

    @Test
    fun seriousVersusCasualIsCapped() {
        val serious = User(profileAnswers = sampleAnswers(intent = "marriage"))
        val casual = User(profileAnswers = sampleAnswers(intent = "casual"))
        assertTrue(CompatibilityCalculator.percent(serious, casual) <= 40)
    }

    private fun sampleAnswers(
        kids: String = "yes",
        hasPet: String = "no",
        datePet: String = "yes",
        intent: String = "serious"
    ): Map<String, Any> = mapOf(
        "relationshipType" to intent,
        "relationshipPace" to "soon",
        "seePartnerFrequency" to "three_to_five",
        "dailyCommunication" to "high",
        "conflictStyle" to "later_calmly",
        "hasPet" to hasPet,
        "wantsPet" to "maybe",
        "dateSomeoneWithPet" to datePet,
        "wantsKids" to kids,
        "kidsTiming" to "two_to_five",
        "marriageView" to "important",
        "familyImportance" to "very",
        "personality" to mapOf(
            "introvert_extrovert" to 40,
            "spontaneous_planner" to 70,
            "minimalist_materialistic" to 20,
            "ambitious_laidback" to 80,
            "traditional_modern" to 55
        ),
        "relationshipValues" to listOf("trust", "humor", "respect"),
        "therapy" to "open",
        "moneyWhenHave" to "save",
        "moneyInRelationship" to "split_even",
        "moneySelf" to "careful",
        "goingOutFrequency" to "once_week",
        "goingOutActivities" to listOf("food", "coffee"),
        "fitnessImportance" to "moderate",
        "freeTime" to listOf("travel", "books", "cooking")
    )
}
