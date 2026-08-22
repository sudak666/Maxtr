package ua.rytm.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import ua.rytm.app.MainActivity
import ua.rytm.app.R

class QuickActionsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)
            bind(views, context, R.id.widget_new_transaction, "finance/new", 1)
            bind(views, context, R.id.widget_shifts, "shifts", 2)
            bind(views, context, R.id.widget_shopping, "shopping", 3)
            manager.updateAppWidget(id, views)
        }
    }

    private fun bind(views: RemoteViews, context: Context, viewId: Int, route: String, requestCode: Int) {
        views.setOnClickPendingIntent(
            viewId,
            PendingIntent.getActivity(
                context,
                requestCode,
                quickActionIntent(context, route),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}

internal fun quickActionIntent(context: Context, route: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("rytm://$route"), context, MainActivity::class.java)
