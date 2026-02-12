package ai.openclaw.android.companion

import java.time.LocalTime

internal object NightModeHelper {
  private const val NIGHT_START_HOUR = 23
  private const val NIGHT_END_HOUR = 7

  fun isNightMode(): Boolean {
    val hour = LocalTime.now().hour
    return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR
  }
}
