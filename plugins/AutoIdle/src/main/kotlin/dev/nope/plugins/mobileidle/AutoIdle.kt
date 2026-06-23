package dev.nope.plugins.autoidle

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.discord.api.presence.ClientStatus
import com.discord.stores.StoreStream
import kotlin.math.max

// Mirrors the official app: going idle here only updates this session's
// reported client status over the gateway, it does not persist a status
// setting, so other sessions (e.g. desktop) are unaffected.
@AliucordPlugin(
        requiresRestart = false,
)
@Suppress("unused")
class AutoIdle : Plugin() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumedActivities = 0
    private var startedActivities = 0

    // Only set when we ourselves switched the session to idle (i.e. it was
    // online beforehand), so we don't clobber a manually chosen dnd/invisible/etc.
    // status, and only restore to online if we were the one who changed it.
    private var markedIdle = false

    // Activity swaps/config changes can briefly report no resumed activity.
    // Debounce that, but keep it short so the gateway update beats app backgrounding.
    private val markIdleRunnable =
            Runnable {
                if (resumedActivities == 0) {
                    markIdleIfOnline()
                }
            }
    private val restoreOnlineRunnable =
            Runnable {
                if (markedIdle && resumedActivities > 0) {
                    restoreOnline()
                }
            }

    private val lifecycleCallbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    logger.debug("AUTOIDLE onActivityStarted: $activity")
                    startedActivities++
                    mainHandler.removeCallbacks(markIdleRunnable)
                    if (markedIdle) {
                        scheduleRestoreOnline()
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    logger.debug("AUTOIDLE onActivityResumed: $activity")
                    resumedActivities++
                    mainHandler.removeCallbacks(markIdleRunnable)
                    if (markedIdle) {
                        scheduleRestoreOnline()
                    }
                }

                override fun onActivityPaused(activity: Activity) {
                    logger.debug("AUTOIDLE onActivityPaused: $activity")
                    resumedActivities = max(0, resumedActivities - 1)
                    mainHandler.removeCallbacks(restoreOnlineRunnable)
                    if (resumedActivities == 0 && !markedIdle) {
                        mainHandler.postDelayed(markIdleRunnable, BACKGROUND_CONFIRMATION_MS)
                    }
                }

                override fun onActivityStopped(activity: Activity) {
                    logger.debug("AUTOIDLE onActivityStopped: $activity")
                    startedActivities = max(0, startedActivities - 1)
                    mainHandler.removeCallbacks(restoreOnlineRunnable)
                    if (startedActivities == 0 && !markedIdle) {
                        mainHandler.removeCallbacks(markIdleRunnable)
                        mainHandler.post(markIdleRunnable)
                    }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }

    private fun markIdleIfOnline() {
        if (markedIdle) return

        val currentPresence = StoreStream.getPresences().localPresence
        if (currentPresence?.status != ClientStatus.ONLINE) {
            logger.debug("AUTOIDLE skipped IDLE; status=${currentPresence?.status}")
            return
        }

        markedIdle = true
        logger.debug("AUTOIDLE sending IDLE")
        StoreStream.getGatewaySocket()
                .presenceUpdate(
                        ClientStatus.IDLE,
                        System.currentTimeMillis(),
                        currentPresence.activities ?: emptyList(),
                        true
                )
    }

    private fun restoreOnline() {
        markedIdle = false
        val currentPresence = StoreStream.getPresences().localPresence
        logger.debug("AUTOIDLE sending ONLINE")
        StoreStream.getGatewaySocket()
                .presenceUpdate(
                        ClientStatus.ONLINE,
                        null,
                        currentPresence?.activities ?: emptyList(),
                        false
                )
    }

    private fun scheduleRestoreOnline() {
        mainHandler.removeCallbacks(restoreOnlineRunnable)
        mainHandler.postDelayed(restoreOnlineRunnable, ONLINE_CONFIRMATION_MS)
    }

    override fun start(context: Context) {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(
                lifecycleCallbacks
        )
    }

    override fun stop(context: Context) {
        mainHandler.removeCallbacks(markIdleRunnable)
        mainHandler.removeCallbacks(restoreOnlineRunnable)
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(
                lifecycleCallbacks
        )
    }

    companion object {
        private const val BACKGROUND_CONFIRMATION_MS = 40L
        private const val ONLINE_CONFIRMATION_MS = 500L
    }
}
