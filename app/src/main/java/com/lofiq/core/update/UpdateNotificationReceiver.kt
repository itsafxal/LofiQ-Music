package com.lofiq.core.update

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lofiq.core.common.LofiQLogger

/**
 * Handles notification actions (e.g. "Later" dismissal) to defer update reminders
 * for 24 hours without repeatedly notifying the user.
 */
class UpdateNotificationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISMISS = "com.lofiq.action.UPDATE_DISMISS"
        const val EXTRA_VERSION = "extra_version"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_DISMISS) {
            val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
            LofiQLogger.d(LofiQLogger.Category.APP, "User deferred update notification for version: $version")

            val prefs = context.getSharedPreferences("lofiq_update_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("last_notified_version", version)
                .putLong("last_notified_timestamp", System.currentTimeMillis())
                .apply()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(AppUpdateManager.UPDATE_NOTIFICATION_ID)
        }
    }
}
