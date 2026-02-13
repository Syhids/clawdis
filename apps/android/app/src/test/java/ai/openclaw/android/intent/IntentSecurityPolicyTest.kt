package ai.openclaw.android.intent

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentSecurityPolicyTest {

  @Test
  fun autoActionsIncludesView() {
    assertTrue(IntentSecurityPolicy.isAutoAction(Intent.ACTION_VIEW))
  }

  @Test
  fun autoActionsIncludesDial() {
    assertTrue(IntentSecurityPolicy.isAutoAction(Intent.ACTION_DIAL))
  }

  @Test
  fun autoActionsIncludesWebSearch() {
    assertTrue(IntentSecurityPolicy.isAutoAction(Intent.ACTION_WEB_SEARCH))
  }

  @Test
  fun autoActionsDoesNotIncludeSend() {
    assertFalse(IntentSecurityPolicy.isAutoAction(Intent.ACTION_SEND))
  }

  @Test
  fun confirmActionsIncludesSend() {
    assertTrue(IntentSecurityPolicy.isConfirmAction(Intent.ACTION_SEND))
  }

  @Test
  fun confirmActionsIncludesSendTo() {
    assertTrue(IntentSecurityPolicy.isConfirmAction(Intent.ACTION_SENDTO))
  }

  @Test
  fun confirmActionsIncludesCall() {
    assertTrue(IntentSecurityPolicy.isConfirmAction(Intent.ACTION_CALL))
  }

  @Test
  fun confirmActionsDoesNotIncludeView() {
    assertFalse(IntentSecurityPolicy.isConfirmAction(Intent.ACTION_VIEW))
  }

  @Test
  fun nullActionIsNotAuto() {
    assertFalse(IntentSecurityPolicy.isAutoAction(null))
  }

  @Test
  fun nullActionIsNotConfirm() {
    assertFalse(IntentSecurityPolicy.isConfirmAction(null))
  }

  @Test
  fun autoActionsIncludesSettingsVariants() {
    assertTrue(IntentSecurityPolicy.isAutoAction("android.settings.WIFI_SETTINGS"))
    assertTrue(IntentSecurityPolicy.isAutoAction("android.settings.BLUETOOTH_SETTINGS"))
    assertTrue(IntentSecurityPolicy.isAutoAction("android.settings.DISPLAY_SETTINGS"))
    assertTrue(IntentSecurityPolicy.isAutoAction("android.settings.LOCATION_SOURCE_SETTINGS"))
  }

  @Test
  fun confirmActionsIncludesAlarmAndTimer() {
    assertTrue(IntentSecurityPolicy.isConfirmAction("android.provider.AlarmClock.ACTION_SET_ALARM"))
    assertTrue(IntentSecurityPolicy.isConfirmAction("android.provider.AlarmClock.ACTION_SET_TIMER"))
  }
}
