package ai.openclaw.android.protocol

enum class OpenClawIntentCommand(val rawValue: String) {
  Launch("intent.launch"),
  Query("intent.query"),
  Apps("intent.apps"),
  Share("intent.share"),
  ;

  companion object {
    const val NamespacePrefix: String = "intent."
  }
}
