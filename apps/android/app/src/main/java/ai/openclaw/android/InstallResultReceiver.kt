package ai.openclaw.android

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

class InstallResultReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
    val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notifId = 9001
    val channelId = "app_update"

    when (status) {
      PackageInstaller.STATUS_PENDING_USER_ACTION -> {
        @Suppress("DEPRECATION")
        val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if (confirmIntent != null) {
          confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

          // Try direct launch first; if BAL blocks it, fall back to a notification
          try {
            context.startActivity(confirmIntent)
            Log.w("openclaw", "app.update: user confirmation requested, launching install dialog")
          } catch (_: Throwable) {
            Log.w("openclaw", "app.update: direct launch blocked, showing notification instead")
          }

          // Always show a high-priority notification the user can tap to confirm
          val pi = PendingIntent.getActivity(
            context, notifId, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
          )
          notifManager.cancel(notifId)
          notifManager.notify(
            notifId,
            Notification.Builder(context, channelId)
              .setSmallIcon(android.R.drawable.stat_sys_download_done)
              .setContentTitle("Update Ready")
              .setContentText("Tap to install the update")
              .setContentIntent(pi)
              .setAutoCancel(true)
              .setFullScreenIntent(pi, true)
              .build(),
          )
        }
      }
      PackageInstaller.STATUS_SUCCESS -> {
        Log.w("openclaw", "app.update: install SUCCESS")
        notifManager.cancel(notifId)
        notifManager.notify(
          notifId,
          Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Update Installed")
            .setContentText("OpenClaw has been updated successfully")
            .setAutoCancel(true)
            .build(),
        )
      }
      else -> {
        Log.e("openclaw", "app.update: install FAILED status=$status message=$message")
        notifManager.cancel(notifId)
        notifManager.notify(
          notifId,
          Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Update Failed")
            .setContentText(message ?: "Installation failed (status $status)")
            .setAutoCancel(true)
            .build(),
        )
      }
    }
  }
}
