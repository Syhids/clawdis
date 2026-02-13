package ai.openclaw.android.intent

import ai.openclaw.android.intent.models.IntentLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IntentShortcutsTest {

  @Test
  fun allShortcutsListHasExpectedSize() {
    val names = IntentShortcuts.allShortcutNames
    assertEquals(10, names.size)
  }

  @Test
  fun allShortcutsListContainsExpectedNames() {
    val names = IntentShortcuts.allShortcutNames.toSet()
    val expected = setOf(
      "alarm", "timer", "navigate", "play_music", "email",
      "settings", "search_web", "open_app", "call", "sms",
    )
    assertEquals(expected, names)
  }

  @Test
  fun unknownShortcutReturnsNull() {
    val request = IntentLaunchRequest(shortcut = "unknown_action")
    val intent = IntentShortcuts.resolve(request)
    assertNull(intent)
  }

  @Test
  fun openAppWithoutPackageReturnsNull() {
    val request = IntentLaunchRequest(shortcut = "open_app")
    val intent = IntentShortcuts.resolve(request)
    assertNull(intent)
  }

  @Test
  fun settingsPageWifiMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("wifi")
    assertEquals("android.settings.WIFI_SETTINGS", action)
  }

  @Test
  fun settingsPageBluetoothMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("bluetooth")
    assertEquals("android.settings.BLUETOOTH_SETTINGS", action)
  }

  @Test
  fun settingsPageDisplayMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("display")
    assertEquals("android.settings.DISPLAY_SETTINGS", action)
  }

  @Test
  fun settingsPageSoundMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("sound")
    assertEquals("android.settings.SOUND_SETTINGS", action)
  }

  @Test
  fun settingsPageSecurityMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("security")
    assertEquals("android.settings.SECURITY_SETTINGS", action)
  }

  @Test
  fun settingsPageLocationMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("location")
    assertEquals("android.settings.LOCATION_SOURCE_SETTINGS", action)
  }

  @Test
  fun settingsPageDeveloperMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("developer")
    assertEquals("android.settings.APPLICATION_DEVELOPMENT_SETTINGS", action)
  }

  @Test
  fun settingsPageNfcMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("nfc")
    assertEquals("android.settings.NFC_SETTINGS", action)
  }

  @Test
  fun settingsPageAirplaneMapsCorrectly() {
    val action = IntentShortcuts.settingsPageToAction("airplane")
    assertEquals("android.settings.AIRPLANE_MODE_SETTINGS", action)
  }

  @Test
  fun settingsPageUnknownMapsToGeneral() {
    val action = IntentShortcuts.settingsPageToAction("unknown")
    assertEquals("android.settings.SETTINGS", action)
  }

  @Test
  fun settingsPageEmptyMapsToGeneral() {
    val action = IntentShortcuts.settingsPageToAction("")
    assertEquals("android.settings.SETTINGS", action)
  }

  @Test
  fun actionForShortcutReturnsCorrectActions() {
    assertEquals("android.intent.action.SET_ALARM", IntentShortcuts.actionForShortcut("alarm"))
    assertEquals("android.intent.action.SET_TIMER", IntentShortcuts.actionForShortcut("timer"))
    assertEquals("android.intent.action.VIEW", IntentShortcuts.actionForShortcut("navigate"))
    assertEquals("android.media.action.MEDIA_PLAY_FROM_SEARCH", IntentShortcuts.actionForShortcut("play_music"))
    assertEquals("android.intent.action.SENDTO", IntentShortcuts.actionForShortcut("email"))
    assertEquals("android.settings.SETTINGS", IntentShortcuts.actionForShortcut("settings"))
    assertEquals("android.intent.action.WEB_SEARCH", IntentShortcuts.actionForShortcut("search_web"))
    assertEquals("android.intent.action.DIAL", IntentShortcuts.actionForShortcut("call"))
    assertEquals("android.intent.action.SENDTO", IntentShortcuts.actionForShortcut("sms"))
  }

  @Test
  fun actionForShortcutReturnsNullForOpenApp() {
    assertNull(IntentShortcuts.actionForShortcut("open_app"))
  }

  @Test
  fun actionForShortcutReturnsNullForUnknown() {
    assertNull(IntentShortcuts.actionForShortcut("nonexistent"))
  }
}
