package dev.nope.plugins.autoidle

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.discord.api.presence.ClientStatus
import com.discord.stores.StoreStream

// Mirrors the official app: going idle here only updates this session's
// reported client status over the gateway, it does not persist a status
// setting, so other sessions (e.g. desktop) are unaffected.
@AliucordPlugin(
    requiresRestart = false,
)
@Suppress("unused")
class AutoIdle : Plugin() {
    private var resumedActivities = 0

    // Only set when we ourselves switched the session to idle (i.e. it was
    // online beforehand), so we don't clobber a manually chosen dnd/invisible/etc.
    // status, and only restore to online if we were the one who changed it.
    private var markedIdle = false

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedActivities++
            if (markedIdle) {
                markedIdle = false
                StoreStream.getGatewaySocket().presenceUpdate(ClientStatus.ONLINE, null, emptyList(), false)
            }
        }

        override fun onActivityPaused(activity: Activity) {
            resumedActivities--
            if (resumedActivities <= 0 && !markedIdle) {
                val currentStatus = StoreStream.getPresences().localPresence?.status
                if (currentStatus == ClientStatus.ONLINE) {
                    markedIdle = true
                    StoreStream.getGatewaySocket().presenceUpdate(ClientStatus.IDLE, System.currentTimeMillis(), emptyList(), true)
                }
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    override fun start(context: Context) {
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    override fun stop(context: Context) {
        (context.applicationContext as Application).unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
    }
}
