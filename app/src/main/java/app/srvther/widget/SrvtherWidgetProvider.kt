package app.srvther.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import app.srvther.MainActivity
import app.srvther.R
import app.srvther.core.SrvtherController
import app.srvther.data.ProfileStore
import app.srvther.model.ConnectionState
import app.srvther.model.isBusy
import app.srvther.model.isConnected

/**
 * Home-screen widget, ported from the merged SrvtherWidgetProvider and adapted
 * to Srvther Mobile's [SrvtherController] / [ProfileStore] architecture.
 *
 * Shows the live connection state and offers one-tap connect/disconnect.
 * Tapping the body opens the app. Connect reuses the last saved profile; if
 * VPN consent is still missing the app is opened instead so the system
 * consent dialog can be shown (the tile uses the same flow).
 *
 * BATTERY: the provider metadata sets updatePeriodMillis=0, so the system
 * never wakes the app on a timer — repaints happen only on real connection
 * state changes via [updateAllWidgets], called from the VPN service's
 * existing state-change hook.
 */
class SrvtherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "app.srvther.WIDGET_TOGGLE"

        /** Repaints every placed widget; called on each connection-state change. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, SrvtherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            val state = SrvtherController.state.value
            ids.forEach { id -> paint(context, manager, id, state) }
        }

        private fun paint(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            state: ConnectionState,
        ) {
            val views = RemoteViews(context.packageName, R.layout.srvther_widget)

            val (text, color) = when (state) {
                is ConnectionState.Connected ->
                    context.getString(R.string.state_connected) to 0xFF34C759.toInt()
                is ConnectionState.Launching, is ConnectionState.Connecting, is ConnectionState.Verifying ->
                    context.getString(R.string.state_connecting) to 0xFFFF9500.toInt()
                is ConnectionState.Reconnecting ->
                    context.getString(R.string.state_reconnecting) to 0xFFFF9500.toInt()
                is ConnectionState.Disconnecting ->
                    context.getString(R.string.state_disconnecting) to 0xFFFF9500.toInt()
                is ConnectionState.Error ->
                    context.getString(R.string.state_error) to 0xFFFF5C5C.toInt()
                else ->
                    context.getString(R.string.state_idle) to 0xFF8E8E93.toInt()
            }
            views.setTextViewText(R.id.widget_status, text)
            views.setTextColor(R.id.widget_status, color)

            // Power button toggles the tunnel.
            val toggle = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, SrvtherWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)

            // Tapping the body opens the app.
            val open = PendingIntent.getActivity(
                context,
                1,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            manager.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = SrvtherController.state.value
        appWidgetIds.forEach { paint(context, appWidgetManager, it, state) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE) return

        val state = SrvtherController.state.value
        if (state.isConnected || state.isBusy) {
            SrvtherController.disconnect(context)
            return
        }
        // VPN consent missing -> open the app so the system dialog can show.
        if (VpnService.prepare(context) != null) {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_CONNECT_ON_LAUNCH, true),
            )
            return
        }
        // Connect with the last saved profile. goAsync: the DataStore read
        // must not block the broadcast's main thread.
        val pending = goAsync()
        Thread {
            try {
                val profile = runBlocking {
                    ProfileStore(context.applicationContext).profile.first()
                }
                SrvtherController.connect(context.applicationContext, profile)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
