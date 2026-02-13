package ai.openclaw.android.intent.models

import kotlinx.serialization.Serializable

@Serializable
data class IntentLaunchRequest(
  val action: String? = null,
  val uri: String? = null,
  val `package`: String? = null,
  val extras: Map<String, String> = emptyMap(),
  val flags: List<String> = emptyList(),
  val expectResult: Boolean = false,
  val category: String? = null,
  val shortcut: String? = null,
  // Shortcut-specific params
  val time: String? = null,
  val label: String? = null,
  val address: String? = null,
  val query: String? = null,
  val provider: String? = null,
  val to: String? = null,
  val subject: String? = null,
  val body: String? = null,
  val page: String? = null,
  val seconds: Int? = null,
  val number: String? = null,
)
