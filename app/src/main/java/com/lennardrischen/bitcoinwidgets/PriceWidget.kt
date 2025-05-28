package com.lennardrischen.bitcoinwidgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Implementation of App Widget functionality.
 */
class PriceWidget : AppWidgetProvider() {
    companion object {
        const val WIDGET_WORK_NAME = "RefreshPriceWidgetWork"
        const val TAG = "PriceWidget"
        const val ACTION_REFRESH_WIDGET = "com.lennardrischen.bitcoinwidgets.ACTION_REFRESH_WIDGET"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate invoked.")

        enqueueOneTimeApiRequestWorker(context, appWidgetIds)

        // There may be multiple widgets active, so update all of them
        /*for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }*/
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "onReceive invoked.")
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
        Log.d(TAG, "onEnabled invoked.")

        // see here https://stackoverflow.com/a/70685721
        // TODO Maybe I can delete this when I added the 15min periodic Worker
        val alwaysPendingWork = OneTimeWorkRequestBuilder<RefreshPriceWidgetWorker>()
            .setInitialDelay(5000L, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "always_pending_work",
            ExistingWorkPolicy.KEEP,
            alwaysPendingWork
        )
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
        Log.d(TAG, "onDisabled invoked.")
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Log.d(TAG, "onDeleted invoked.")
        super.onDeleted(context, appWidgetIds)
    }

    private fun enqueueOneTimeApiRequestWorker(context: Context, appWidgetIds: IntArray) {
        // Worker requires internet connection
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putIntArray(RefreshPriceWidgetWorker.WIDGET_IDS_KEY, appWidgetIds)
            .build()

        val immediateWorkRequest = OneTimeWorkRequestBuilder<RefreshPriceWidgetWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueue(immediateWorkRequest)
        Log.d(TAG, "One time RefreshPriceWidgetWorker scheduled.")
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val widgetText = context.getString(R.string.appwidget_text)
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.price_widget)
    //views.setTextViewText(R.id.appwidget_text, widgetText)

    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}