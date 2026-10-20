package com.example.morvo

/**
 * Weighted compatibility score, 0–100.
 *
 * Points before penalties sum to 100:
 * - High (46): marriage 10, family 8, pace 8, seeing partner 8, daily communication 6, conflict 6.
 * - Medium (24): each money question 4, going-out frequency 4, fitness 4, therapy 4.
 * - Overlap (15): values 6, going-out activities 5, free time 4, using Jaccard.
 * - Personality (15): average closeness of the five 0–100 sliders.
 *
 * Ordered answers score by distance along the scale. "Depends" and "undecided" score 60% of that item.
 * Therapy "not interested" mismatches the other three answers; those three score 60% against each other.
 *
 * Deal-breakers apply after the percentage:
 * - Kids yes vs no: −40 and cap 45. No vs already have kids: −25 and cap 60.
 * - "Would not date a pet owner" vs the other person has a pet: −30 and cap 55.
 * - Serious/marriage intent vs casual/no-expectation: −35 and cap 40.
 *
 * Empty answers on either person return 0.
 */
object CompatibilityCalculator {

    private const val W_MARRIAGE = 10
    private const val W_FAMILY = 8
    private const val W_PACE = 8
    private const val W_SEE = 8
    private const val W_COMM = 6
    private const val W_CONFLICT = 6
    private const val W_MONEY = 4
    private const val W_OUT = 4
    private const val W_FITNESS = 4
    private const val W_THERAPY = 4
    private const val W_VALUES = 6
    private const val W_ACTIVITIES = 5
    private const val W_FREE = 4
    private const val W_PERSONALITY = 15

    private val seriousIntent = setOf("serious", "long_term_marriage", "marriage")
    private val casualIntent = setOf("casual", "dating_no_expectation")
    private val softAnswers = setOf("depends", "undecided", "not_sure")
    private val therapyOpen = setOf("current", "past", "open")

    fun percent(me: User, other: User): Int {
        val a = me.profileAnswers
        val b = other.profileAnswers
        if (a.isEmpty() || b.isEmpty()) return 0

        var earned = 0
        var possible = 0

        fun add(points: Int, weight: Int) {
            earned += points
            possible += weight
        }

        add(ordered(a, b, "marriageView", listOf("important", "nice_not_required", "not_interested"), W_MARRIAGE), W_MARRIAGE)
        add(ordered(a, b, "familyImportance", listOf("very", "quite", "little", "none"), W_FAMILY), W_FAMILY)
        add(ordered(a, b, "relationshipPace", listOf("slow", "soon", "family_coming_years"), W_PACE), W_PACE)
        add(ordered(a, b, "seePartnerFrequency", listOf("every_day", "three_to_five", "one_to_two"), W_SEE), W_SEE)
        add(ordered(a, b, "dailyCommunication", listOf("high", "medium", "low"), W_COMM), W_COMM)
        add(exact(a, b, "conflictStyle", W_CONFLICT), W_CONFLICT)

        add(exact(a, b, "moneyWhenHave", W_MONEY), W_MONEY)
        add(exact(a, b, "moneyInRelationship", W_MONEY), W_MONEY)
        add(ordered(a, b, "moneySelf", listOf("very_saver", "careful", "balanced", "enjoy", "spontaneous"), W_MONEY), W_MONEY)
        add(ordered(a, b, "goingOutFrequency", listOf("very_often", "two_three_week", "once_week", "rarely"), W_OUT), W_OUT)
        add(ordered(a, b, "fitnessImportance", listOf("none", "little", "moderate", "high", "central"), W_FITNESS), W_FITNESS)
        add(therapy(a, b), W_THERAPY)

        add(overlap(a, b, "relationshipValues", W_VALUES), W_VALUES)
        add(overlap(a, b, "goingOutActivities", W_ACTIVITIES), W_ACTIVITIES)
        add(overlap(a, b, "freeTime", W_FREE), W_FREE)
        add(personality(a, b), W_PERSONALITY)

        if (possible == 0) return 0
        var score = earned * 100 / possible
        var cap = 100

        val kids = kidsDealBreaker(ProfileAnswers.text(a, "wantsKids"), ProfileAnswers.text(b, "wantsKids"))
        score -= kids.penalty
        cap = minOf(cap, kids.cap)

        if (petDealBreaker(a, b)) {
            score -= 30
            cap = minOf(cap, 55)
        }
        if (intentDealBreaker(ProfileAnswers.text(a, "relationshipType"), ProfileAnswers.text(b, "relationshipType"))) {
            score -= 35
            cap = minOf(cap, 40)
        }

        return score.coerceIn(0, cap)
    }

    private data class Deal(val penalty: Int, val cap: Int)

    private fun kidsDealBreaker(left: String?, right: String?): Deal {
        if (left == null || right == null) return Deal(0, 100)
        val yesNo = (left == "yes" && right == "no") || (left == "no" && right == "yes")
        if (yesNo) return Deal(40, 45)
        val already = (left == "no" && right == "already_have") || (left == "already_have" && right == "no")
        if (already) return Deal(25, 60)
        return Deal(0, 100)
    }

    private fun petDealBreaker(a: Map<String, Any>, b: Map<String, Any>): Boolean {
        val aHas = ProfileAnswers.text(a, "hasPet")
        val bHas = ProfileAnswers.text(b, "hasPet")
        val aDate = ProfileAnswers.text(a, "dateSomeoneWithPet")
        val bDate = ProfileAnswers.text(b, "dateSomeoneWithPet")
        return (aDate == "no" && bHas == "yes") || (bDate == "no" && aHas == "yes")
    }

    private fun intentDealBreaker(left: String?, right: String?): Boolean {
        if (left == null || right == null) return false
        return (left in seriousIntent && right in casualIntent) || (right in seriousIntent && left in casualIntent)
    }

    private fun exact(a: Map<String, Any>, b: Map<String, Any>, key: String, weight: Int): Int {
        val left = ProfileAnswers.text(a, key) ?: return 0
        val right = ProfileAnswers.text(b, key) ?: return 0
        if (left in softAnswers || right in softAnswers) return weight * 6 / 10
        return if (left == right) weight else 0
    }

    private fun ordered(a: Map<String, Any>, b: Map<String, Any>, key: String, order: List<String>, weight: Int): Int {
        val left = ProfileAnswers.text(a, key) ?: return 0
        val right = ProfileAnswers.text(b, key) ?: return 0
        if (left in softAnswers || right in softAnswers) return weight * 6 / 10
        val leftIndex = order.indexOf(left)
        val rightIndex = order.indexOf(right)
        if (leftIndex < 0 || rightIndex < 0) return 0
        val span = (order.size - 1).coerceAtLeast(1)
        val distance = kotlin.math.abs(leftIndex - rightIndex)
        return weight * (span - distance) / span
    }

    private fun therapy(a: Map<String, Any>, b: Map<String, Any>): Int {
        val left = ProfileAnswers.text(a, "therapy") ?: return 0
        val right = ProfileAnswers.text(b, "therapy") ?: return 0
        if (left == right) return W_THERAPY
        if (left in therapyOpen && right in therapyOpen) return W_THERAPY * 6 / 10
        return 0
    }

    private fun overlap(a: Map<String, Any>, b: Map<String, Any>, key: String, weight: Int): Int {
        val left = ProfileAnswers.texts(a, key)
        val right = ProfileAnswers.texts(b, key)
        val union = left.union(right).size
        if (union == 0) return 0
        val shared = left.intersect(right.toSet()).size
        return weight * shared / union
    }

    private fun personality(a: Map<String, Any>, b: Map<String, Any>): Int {
        val left = ProfileAnswers.sliders(a, "personality")
        val right = ProfileAnswers.sliders(b, "personality")
        val distances = ProfileCatalog.personalityKeys.mapNotNull { key ->
            val lv = left[key]
            val rv = right[key]
            if (lv == null || rv == null) null else 100 - kotlin.math.abs(lv - rv)
        }
        if (distances.isEmpty()) return 0
        val closeness = distances.sum() / distances.size
        return W_PERSONALITY * closeness / 100
    }
}
