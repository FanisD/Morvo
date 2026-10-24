package com.example.morvo

import android.content.Context

object ProfileCatalog {
    data class Option(val key: String, val labelRes: Int)
    data class SliderSpec(val key: String, val leftRes: Int, val rightRes: Int)
    data class Question(
        val key: String,
        val promptRes: Int,
        val options: List<Option> = emptyList(),
        val sliders: List<SliderSpec> = emptyList(),
        val maxSelect: Int = 1,
        val hintRes: Int = 0
    )
    data class Section(val titleRes: Int, val questions: List<Question>)

    const val SLIDER_INTROVERT = "introvert_extrovert"
    const val SLIDER_SPONTANEOUS = "spontaneous_planner"
    const val SLIDER_MINIMALIST = "minimalist_materialistic"
    const val SLIDER_AMBITIOUS = "ambitious_laidback"
    const val SLIDER_TRADITIONAL = "traditional_modern"

    val personalityKeys = listOf(
        SLIDER_INTROVERT,
        SLIDER_SPONTANEOUS,
        SLIDER_MINIMALIST,
        SLIDER_AMBITIOUS,
        SLIDER_TRADITIONAL
    )

    val sections: List<Section> = listOf(
        Section(
            R.string.section_relationship,
            listOf(
                Question(
                    "relationshipType",
                    R.string.q_relationship_type,
                    options(
                        "serious" to R.string.opt_rel_serious,
                        "long_term_marriage" to R.string.opt_rel_long_term,
                        "marriage" to R.string.opt_rel_marriage,
                        "casual" to R.string.opt_rel_casual,
                        "dating_no_expectation" to R.string.opt_rel_dating,
                        "not_sure" to R.string.opt_rel_unsure
                    )
                ),
                Question(
                    "relationshipPace",
                    R.string.q_relationship_pace,
                    options(
                        "slow" to R.string.opt_pace_slow,
                        "soon" to R.string.opt_pace_soon,
                        "family_coming_years" to R.string.opt_pace_family
                    )
                )
            )
        ),
        Section(
            R.string.section_style,
            listOf(
                Question(
                    "seePartnerFrequency",
                    R.string.q_see_partner,
                    options(
                        "every_day" to R.string.opt_see_daily,
                        "three_to_five" to R.string.opt_see_3_5,
                        "one_to_two" to R.string.opt_see_1_2,
                        "depends" to R.string.opt_see_depends
                    )
                ),
                Question(
                    "dailyCommunication",
                    R.string.q_daily_communication,
                    options(
                        "high" to R.string.opt_comm_high,
                        "medium" to R.string.opt_comm_medium,
                        "low" to R.string.opt_comm_low
                    )
                ),
                Question(
                    "conflictStyle",
                    R.string.q_conflict,
                    options(
                        "immediately" to R.string.opt_conflict_now,
                        "later_calmly" to R.string.opt_conflict_later
                    )
                )
            )
        ),
        Section(
            R.string.section_pets,
            listOf(
                Question("hasPet", R.string.q_has_pet, yesNo()),
                Question(
                    "wantsPet",
                    R.string.q_wants_pet,
                    options("yes" to R.string.opt_yes, "no" to R.string.opt_no, "maybe" to R.string.opt_maybe)
                ),
                Question(
                    "dateSomeoneWithPet",
                    R.string.q_date_pet,
                    options("yes" to R.string.opt_yes, "maybe" to R.string.opt_maybe, "no" to R.string.opt_no)
                )
            )
        ),
        Section(
            R.string.section_family,
            listOf(
                Question(
                    "wantsKids",
                    R.string.q_wants_kids,
                    options(
                        "yes" to R.string.opt_yes,
                        "no" to R.string.opt_no,
                        "maybe" to R.string.opt_maybe,
                        "already_have" to R.string.opt_kids_already
                    )
                ),
                Question(
                    "kidsTiming",
                    R.string.q_kids_timing,
                    options(
                        "soon" to R.string.opt_kids_soon,
                        "two_to_five" to R.string.opt_kids_2_5,
                        "later" to R.string.opt_kids_later,
                        "undecided" to R.string.opt_undecided
                    )
                ),
                Question(
                    "marriageView",
                    R.string.q_marriage,
                    options(
                        "important" to R.string.opt_marriage_important,
                        "nice_not_required" to R.string.opt_marriage_nice,
                        "not_interested" to R.string.opt_marriage_no
                    )
                ),
                Question(
                    "familyImportance",
                    R.string.q_family_importance,
                    options(
                        "very" to R.string.opt_family_very,
                        "quite" to R.string.opt_family_quite,
                        "little" to R.string.opt_family_little,
                        "none" to R.string.opt_family_none,
                        "undecided" to R.string.opt_undecided
                    )
                )
            )
        ),
        Section(
            R.string.section_personality,
            listOf(
                Question(
                    "personality",
                    R.string.q_personality,
                    sliders = listOf(
                        SliderSpec(SLIDER_INTROVERT, R.string.slider_introvert, R.string.slider_extrovert),
                        SliderSpec(SLIDER_SPONTANEOUS, R.string.slider_spontaneous, R.string.slider_planner),
                        SliderSpec(SLIDER_MINIMALIST, R.string.slider_minimalist, R.string.slider_materialistic),
                        SliderSpec(SLIDER_AMBITIOUS, R.string.slider_ambitious, R.string.slider_laidback),
                        SliderSpec(SLIDER_TRADITIONAL, R.string.slider_traditional, R.string.slider_modern)
                    )
                ),
                Question(
                    "relationshipValues",
                    R.string.q_values,
                    options(
                        "trust" to R.string.opt_value_trust,
                        "communication" to R.string.opt_value_communication,
                        "humor" to R.string.opt_value_humor,
                        "respect" to R.string.opt_value_respect,
                        "sexual_compatibility" to R.string.opt_value_sexual,
                        "financial_stability" to R.string.opt_value_money,
                        "shared_values" to R.string.opt_value_shared,
                        "adventure" to R.string.opt_value_adventure,
                        "family" to R.string.opt_value_family,
                        "personal_space" to R.string.opt_value_space,
                        "ambition" to R.string.opt_value_ambition,
                        "companionship" to R.string.opt_value_companionship
                    ),
                    maxSelect = 5,
                    hintRes = R.string.hint_pick_5
                )
            )
        ),
        Section(
            R.string.section_therapy,
            listOf(
                Question(
                    "therapy",
                    R.string.q_therapy,
                    options(
                        "current" to R.string.opt_therapy_current,
                        "past" to R.string.opt_therapy_past,
                        "open" to R.string.opt_therapy_open,
                        "not_interested" to R.string.opt_therapy_no
                    )
                )
            )
        ),
        Section(
            R.string.section_money,
            listOf(
                Question(
                    "moneyWhenHave",
                    R.string.q_money_have,
                    options(
                        "save" to R.string.opt_money_save,
                        "invest" to R.string.opt_money_invest,
                        "travel" to R.string.opt_money_travel,
                        "buy_things" to R.string.opt_money_buy,
                        "experiences" to R.string.opt_money_experiences
                    )
                ),
                Question(
                    "moneyInRelationship",
                    R.string.q_money_relationship,
                    options(
                        "split_even" to R.string.opt_money_split,
                        "by_income" to R.string.opt_money_income,
                        "shared" to R.string.opt_money_shared,
                        "depends" to R.string.opt_money_depends
                    )
                ),
                Question(
                    "moneySelf",
                    R.string.q_money_self,
                    options(
                        "very_saver" to R.string.opt_self_saver,
                        "careful" to R.string.opt_self_careful,
                        "balanced" to R.string.opt_self_balanced,
                        "enjoy" to R.string.opt_self_enjoy,
                        "spontaneous" to R.string.opt_self_spontaneous
                    )
                )
            )
        ),
        Section(
            R.string.section_going_out,
            listOf(
                Question(
                    "goingOutFrequency",
                    R.string.q_going_out_frequency,
                    options(
                        "very_often" to R.string.opt_out_often,
                        "two_three_week" to R.string.opt_out_2_3,
                        "once_week" to R.string.opt_out_once,
                        "rarely" to R.string.opt_out_rarely
                    )
                ),
                Question(
                    "goingOutActivities",
                    R.string.q_going_out_activities,
                    options(
                        "food" to R.string.opt_out_food,
                        "bars" to R.string.opt_out_bars,
                        "bouzoukia" to R.string.opt_out_bouzoukia,
                        "clubs" to R.string.opt_out_clubs,
                        "coffee" to R.string.opt_out_coffee,
                        "live_music" to R.string.opt_out_live,
                        "theater" to R.string.opt_out_theater,
                        "cinema" to R.string.opt_out_cinema,
                        "concerts" to R.string.opt_out_concerts,
                        "city_walks" to R.string.opt_out_walks
                    ),
                    maxSelect = 5,
                    hintRes = R.string.hint_pick_5
                )
            )
        ),
        Section(
            R.string.section_fitness,
            listOf(
                Question(
                    "fitnessImportance",
                    R.string.q_fitness,
                    options(
                        "none" to R.string.opt_fit_none,
                        "little" to R.string.opt_fit_little,
                        "moderate" to R.string.opt_fit_moderate,
                        "high" to R.string.opt_fit_high,
                        "central" to R.string.opt_fit_central
                    )
                )
            )
        ),
        Section(
            R.string.section_free_time,
            listOf(
                Question(
                    "freeTime",
                    R.string.q_free_time,
                    options(
                        "travel" to R.string.opt_free_travel,
                        "beach" to R.string.opt_free_beach,
                        "hiking" to R.string.opt_free_hiking,
                        "gym" to R.string.opt_free_gym,
                        "cooking" to R.string.opt_free_cooking,
                        "gaming" to R.string.opt_free_gaming,
                        "books" to R.string.opt_free_books,
                        "museums" to R.string.opt_free_museums,
                        "art" to R.string.opt_free_art,
                        "photography" to R.string.opt_free_photo,
                        "dance" to R.string.opt_free_dance,
                        "series" to R.string.opt_free_series,
                        "cinema" to R.string.opt_free_cinema,
                        "music" to R.string.opt_free_music
                    ),
                    maxSelect = 7,
                    hintRes = R.string.hint_pick_7
                )
            )
        )
    )

    fun label(context: Context, questionKey: String, optionKey: String): String {
        val option = sections.flatMap { it.questions }
            .firstOrNull { it.key == questionKey }
            ?.options
            ?.firstOrNull { it.key == optionKey }
        return if (option == null) optionKey else context.getString(option.labelRes)
    }

    fun highlights(context: Context, user: User): String {
        val answers = user.profileAnswers
        val lines = mutableListOf<String>()
        ProfileAnswers.text(answers, "relationshipType")?.let {
            lines.add(label(context, "relationshipType", it))
        }
        ProfileAnswers.text(answers, "wantsKids")?.let {
            lines.add(context.getString(R.string.highlight_kids, label(context, "wantsKids", it)))
        }
        val values = ProfileAnswers.texts(answers, "relationshipValues").take(3).map {
            label(context, "relationshipValues", it)
        }
        if (values.isNotEmpty()) lines.add(values.joinToString(" · "))
        return if (lines.isEmpty()) context.getString(R.string.highlights_empty) else lines.joinToString("\n")
    }

    private fun options(vararg pairs: Pair<String, Int>) = pairs.map { Option(it.first, it.second) }

    private fun yesNo() = options("yes" to R.string.opt_yes, "no" to R.string.opt_no)
}
