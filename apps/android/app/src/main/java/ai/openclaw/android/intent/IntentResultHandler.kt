package ai.openclaw.android.intent

import android.content.Intent
import kotlinx.coroutines.CompletableDeferred

class IntentResultHandler {

  data class PendingResult(
    val requestKey: String,
    val deferred: CompletableDeferred<ActivityResultData>,
  )

  data class ActivityResultData(
    val resultCode: Int,
    val data: Intent?,
  )

  private val pending = mutableMapOf<String, PendingResult>()

  @Synchronized
  fun register(requestKey: String): CompletableDeferred<ActivityResultData> {
    val deferred = CompletableDeferred<ActivityResultData>()
    pending[requestKey] = PendingResult(requestKey, deferred)
    return deferred
  }

  @Synchronized
  fun complete(requestKey: String, resultCode: Int, data: Intent?) {
    val entry = pending.remove(requestKey) ?: return
    entry.deferred.complete(ActivityResultData(resultCode = resultCode, data = data))
  }

  @Synchronized
  fun cancel(requestKey: String) {
    val entry = pending.remove(requestKey) ?: return
    entry.deferred.cancel()
  }

  @Synchronized
  fun hasPending(): Boolean = pending.isNotEmpty()
}
