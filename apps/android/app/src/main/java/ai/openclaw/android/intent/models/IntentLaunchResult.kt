package ai.openclaw.android.intent.models

import kotlinx.serialization.Serializable

@Serializable
data class IntentLaunchResult(
  val ok: Boolean,
  val launched: Boolean = false,
  val resolvedApp: String? = null,
  val appName: String? = null,
  val error: String? = null,
  val resultCode: Int? = null,
  val resultData: String? = null,
)
