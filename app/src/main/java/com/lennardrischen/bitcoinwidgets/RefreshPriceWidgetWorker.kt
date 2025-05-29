package com.lennardrischen.bitcoinwidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.icu.text.NumberFormat
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import android.content.ComponentName
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap

data class BitcoinPriceData(
    val price: Double,
    val change24h: Double
)

data class BitcoinMarketChartData(
    val prices: List<Pair<Long, Double>>
)

class RefreshPriceWidgetWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    companion object {
        const val TAG = "RefreshPriceWidgetWorker"
        const val WIDGET_IDS_KEY = "widget_ids"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork invoked.")

        return try {
            val bitcoinPriceData = ApiRequester.fetchBitcoinPriceData()
            Log.d(TAG, "Fetched following Bitcoin price data: $bitcoinPriceData")

            val bitcoinMarketChartData = ApiRequester.fetchBitcoinMarketChartData()
            Log.d(TAG, "Fetched following Bitcoin market chart data: $bitcoinMarketChartData")

            WidgetUpdater.updatePriceWidget(applicationContext, bitcoinPriceData, bitcoinMarketChartData)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing doWork function: ${e.message}", e)
            Result.retry()
        }
    }
}

class ApiRequester() {
    companion object {
        private val client = OkHttpClient()
        private const val BASE_URL = "https://api.coingecko.com/api/v3/"
        private const val TAG = "ApiRequester"

        suspend fun fetchBitcoinPriceData(): BitcoinPriceData {
            val jsonObject = sendApiRequest("simple/price?ids=bitcoin&vs_currencies=eur&include_24hr_change=true")
            val bitcoinJsonObject = jsonObject.getJSONObject("bitcoin")

            val price = bitcoinJsonObject.getDouble("eur")
            val change24h = bitcoinJsonObject.getDouble("eur_24h_change")

            return BitcoinPriceData(price, change24h)
        }

        suspend fun fetchBitcoinMarketChartData(): BitcoinMarketChartData {
            val jsonObject = sendApiRequest("coins/bitcoin/market_chart?vs_currency=eur&days=1")

            val pricesArray = jsonObject.getJSONArray("prices")

            val prices = mutableListOf<Pair<Long, Double>>()

            for (i in 0 until pricesArray.length()) {
                val priceEntry = pricesArray.getJSONArray(i)
                val timestamp = priceEntry.getLong(0)
                val price = priceEntry.getDouble(1)
                prices.add(Pair(timestamp, price))
            }

            return BitcoinMarketChartData(prices)
        }

        private suspend fun sendApiRequest(endpoint: String): JSONObject {
            val request = Request.Builder().url(BASE_URL + endpoint).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw IOException("API call failed with code ${response.code()} and the following message: ${response.message()}")
            }

            val responseBody = response.body()?.string()
                ?: throw IOException("API response body is empty!")

            try {
                return JSONObject(responseBody)
            } catch (e: JSONException) {
                Log.e(TAG, "Error while parsing JSON", e)
                throw e
            }
        }
    }
}

class WidgetUpdater() {
    companion object {
        private const val TAG = "WidgetUpdater"

        fun updatePriceWidget(context: Context, bitcoinPriceData: BitcoinPriceData, bitcoinMarketChartData: BitcoinMarketChartData) {
            val price = bitcoinPriceData.price
            val formattedPrice = formatPrice(price)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, PriceWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

            for (appWidgetId in appWidgetIds) {
                // Construct the RemoteViews object
                val views = RemoteViews(context.packageName, R.layout.price_widget)

                views.setTextViewText(R.id.appwidget_price_text, formattedPrice)

                val (width, height) = getAppWidgetMaxWidthAndHeight(context, appWidgetManager, appWidgetId)
                val bitmap = drawPriceChart(bitcoinMarketChartData, width, height)

                views.setImageViewBitmap(R.id.appwidget_chart_image_view, bitmap)

                // Instruct the widget manager to update the widget
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun getAppWidgetMaxWidthAndHeight(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            val density = context.resources.displayMetrics.density
            val widthPx = (maxWidthDp * density).toInt()
            val heightPx = (maxHeightDp * density).toInt()

            return Pair(widthPx, heightPx)
        }

        private fun formatPrice(price: Double): String {
            val formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
            formatter.maximumFractionDigits = 0
            formatter.minimumFractionDigits = 0
            return formatter.format(price)
        }

        private fun drawPriceChart(bitcoinMarketChartData: BitcoinMarketChartData, width: Int, height: Int): Bitmap {
            val prices: List<Float> = bitcoinMarketChartData.prices.map { it.second.toFloat() }

            Log.d(TAG, "Prices: ${prices}")

            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            //canvas.drawColor(Color.LTGRAY)

            val paint = Paint().apply {
                color = Color.parseColor("#FFFFFFFF")
                strokeWidth = minOf(width, height) * 0.02f // determine based on the smaller dimension
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            val minValue = prices.minOrNull() ?: 0f
            val maxValue = prices.maxOrNull() ?: 1f

            val xRatio: Float = width.toFloat() / (prices.size - 1)
            val yRatio: Float = height / (maxValue - minValue)

            val path = Path()

            for (i in prices.indices) {
                val x = (i * xRatio)
                // y-axes top is 0
                val y = (height - (prices[i] - minValue) * yRatio)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)

            return bitmap
        }
    }
}