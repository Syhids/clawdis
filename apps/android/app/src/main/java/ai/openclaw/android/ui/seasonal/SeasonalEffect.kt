package ai.openclaw.android.ui.seasonal

import java.time.MonthDay

enum class SeasonalEffect(val label: String, val rawValue: String) {
    AUTO("Auto", "auto"),
    WINTER("Winter ❄️", "winter"),
    SPRING("Spring 🌸", "spring"),
    SUMMER("Summer ☀️", "summer"),
    AUTUMN("Autumn 🍂", "autumn"),
    OFF("Off", "off");

    companion object {
        fun fromRawValue(raw: String?): SeasonalEffect =
            entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) } ?: AUTO

        /** Resolve AUTO to the actual season based on the current date. Returns OFF for OFF. */
        fun resolveEffect(pref: SeasonalEffect): SeasonalEffect? {
            if (pref == OFF) return null
            if (pref != AUTO) return pref
            return currentSeason()
        }

        fun currentSeason(): SeasonalEffect {
            val now = MonthDay.now()
            val month = now.monthValue
            return when (month) {
                12, 1, 2 -> WINTER
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                in 9..11 -> AUTUMN
                else -> WINTER
            }
        }
    }
}
