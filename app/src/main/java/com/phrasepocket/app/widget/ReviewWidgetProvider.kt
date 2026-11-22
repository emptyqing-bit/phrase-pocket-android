package com.phrasepocket.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.phrasepocket.app.R
import com.phrasepocket.app.data.AppDatabase
import com.phrasepocket.app.ui.MainActivity
import java.util.concurrent.Executors

/** Home-screen review card: shows a random saved entry, refreshes on demand. */
class ReviewWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        executor.execute {
            try {
                ids.forEach { updateWidget(appContext, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ReviewWidgetProvider::class.java)
            onUpdate(context, manager, manager.getAppWidgetIds(component))
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.phrasepocket.app.action.WIDGET_REFRESH"
        private val executor = Executors.newSingleThreadExecutor()

        fun refreshAll(context: Context) {
            val intent = Intent(context, ReviewWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        private fun updateWidget(appContext: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(appContext.packageName, R.layout.widget_review)
            val entry = AppDatabase.get(appContext).entryDao().randomEntry()
            if (entry == null) {
                views.setTextViewText(R.id.widget_text, appContext.getString(R.string.widget_empty))
                views.setTextViewText(R.id.widget_meaning, "")
            } else {
                views.setTextViewText(R.id.widget_text, entry.text)
                views.setTextViewText(R.id.widget_meaning, entry.meaning)
            }

            val openIntent = PendingIntent.getActivity(
                appContext, 0, Intent(appContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)

            val refreshIntent = PendingIntent.getBroadcast(
                appContext, 1,
                Intent(appContext, ReviewWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent)

            manager.updateAppWidget(id, views)
        }
    }
}
