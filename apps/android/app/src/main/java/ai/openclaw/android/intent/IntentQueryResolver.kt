package ai.openclaw.android.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class IntentQueryResolver(context: Context) {
  private val appContext = context.applicationContext

  fun canResolve(intent: Intent): Boolean {
    val pm = appContext.packageManager
    return intent.resolveActivity(pm) != null
  }

  fun resolveAppName(packageName: String): String? {
    val pm = appContext.packageManager
    return try {
      val info = pm.getApplicationInfo(packageName, 0)
      pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
      null
    }
  }

  fun resolveAppNameForIntent(intent: Intent): String? {
    val pm = appContext.packageManager
    val resolveInfo = intent.resolveActivity(pm) ?: return null
    val pkg = resolveInfo.packageName
    return resolveAppName(pkg)
  }

  fun getLaunchIntentForPackage(packageName: String): Intent? {
    val pm = appContext.packageManager
    return pm.getLaunchIntentForPackage(packageName)
  }

  fun queryInstalledApps(filter: String?): String {
    val pm = appContext.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Suppress("DEPRECATION")
    val activities = pm.queryIntentActivities(intent, 0)

    val filterLower = filter?.trim()?.lowercase().orEmpty()
    val apps = activities
      .mapNotNull { resolveInfo ->
        val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
        val pkg = appInfo.packageName ?: return@mapNotNull null
        val name = pm.getApplicationLabel(appInfo).toString()
        if (filterLower.isNotEmpty() &&
          !name.lowercase().contains(filterLower) &&
          !pkg.lowercase().contains(filterLower)
        ) {
          return@mapNotNull null
        }
        buildJsonObject {
          put("package", JsonPrimitive(pkg))
          put("name", JsonPrimitive(name))
        }
      }
      .distinctBy { it["package"].toString() }

    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("apps", JsonArray(apps))
      put("count", JsonPrimitive(apps.size))
    }.toString()
  }

  fun queryCanResolve(action: String?, uri: String?, packageName: String?): String {
    val intent = Intent()
    if (!action.isNullOrBlank()) intent.action = action
    if (!uri.isNullOrBlank()) intent.data = Uri.parse(uri)
    if (!packageName.isNullOrBlank()) intent.setPackage(packageName)

    val canResolve = canResolve(intent)
    val resolvedPkg = intent.resolveActivity(appContext.packageManager)?.packageName
    val appName = resolvedPkg?.let { resolveAppName(it) }

    return buildJsonObject {
      put("ok", JsonPrimitive(true))
      put("canResolve", JsonPrimitive(canResolve))
      if (resolvedPkg != null) put("resolvedApp", JsonPrimitive(resolvedPkg))
      if (appName != null) put("appName", JsonPrimitive(appName))
    }.toString()
  }
}
