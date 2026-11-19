package com.lofiq.core.fcm

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lofiq.core.common.LofiQLogger
import com.lofiq.core.update.AppUpdateManager
import com.lofiq.data.repository.HomeFeedCacheManager
import com.lofiq.data.sync.BackgroundSyncWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Event-Driven Remote Data & Update Invalidation Service (FCM).
 *
 * Receives lightweight server-pushed invalidation events:
 * - "catalog_updated": Invalidates specific or all cached home shelves.
 * - "app_update": Discovers new releases and shows user-controlled notifications.
 * - "new_release": Invalidates the new releases shelf.
 *
 * Operates without persistent wake locks or battery-draining continuous polling.
 */
class LofiQFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FcmEntryPoint {
        fun homeFeedCacheManager(): HomeFeedCacheManager
        fun appUpdateManager(): AppUpdateManager
    }

    private fun getEntryPoint(): FcmEntryPoint {
        return EntryPointAccessors.fromApplication(
            applicationContext,
            FcmEntryPoint::class.java
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        LofiQLogger.i(LofiQLogger.Category.APP, "FCM registration token refreshed")
        val prefs = getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FCM_TOKEN, token)
            .putLong(KEY_FCM_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        if (data.isEmpty()) {
            LofiQLogger.d(LofiQLogger.Category.APP, "FCM received empty data payload")
            return
        }

        val action = data["action"] ?: data["type"] ?: ""
        LofiQLogger.i(LofiQLogger.Category.APP, "FCM invalidation event received: action=$action")

        serviceScope.launch {
            try {
                val entryPoint = getEntryPoint()

                when (action.lowercase()) {
                    "catalog_updated", "shelf_invalidation" -> {
                        val shelfId = data["shelf_id"]
                        if (!shelfId.isNullOrBlank()) {
                            entryPoint.homeFeedCacheManager().invalidateShelf(shelfId)
                            LofiQLogger.i(LofiQLogger.Category.CACHE, "FCM invalidated shelf: $shelfId")
                        } else {
                            entryPoint.homeFeedCacheManager().clearAllCaches()
                            LofiQLogger.i(LofiQLogger.Category.CACHE, "FCM invalidated all shelves")
                        }
                        BackgroundSyncWorker.triggerOneTimeSync(applicationContext)
                    }

                    "new_release" -> {
                        entryPoint.homeFeedCacheManager().invalidateShelf("new_releases")
                        BackgroundSyncWorker.triggerOneTimeSync(applicationContext)
                    }

                    "app_update" -> {
                        entryPoint.appUpdateManager().checkForUpdateAndNotify(applicationContext)
                    }

                    else -> {
                        if (data.containsKey("version_code") || data.containsKey("version_name")) {
                            entryPoint.appUpdateManager().checkForUpdateAndNotify(applicationContext)
                        } else {
                            LofiQLogger.d(LofiQLogger.Category.APP, "FCM received unhandled action: $action")
                        }
                    }
                }
            } catch (e: Exception) {
                LofiQLogger.w(LofiQLogger.Category.APP, "Error processing FCM message: ${e.message}")
            }
        }
    }

    companion object {
        private const val PREFS_FCM = "lofiq_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"
        private const val KEY_FCM_TIMESTAMP = "fcm_timestamp"

        fun getSavedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }
    }
}
