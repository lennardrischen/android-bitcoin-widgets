package com.lennardrischen.bitcoinwidgets

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class BitcoinPriceData(
    val price: Double,
    val change24h: Double
)

class BitcoinMarketChartData(
    val prices: List<Pair<Long, Double>>
)

class RefreshPriceWidgetWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    companion object {
        const val TAG = "RefreshPriceWidgetWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "onWork invoked.")

        return try {
            val bitcoinPriceData = ApiRequester.fetchBitcoinPriceData()
            val bitcoinMarketChartData = ApiRequester.fetchBitcoinMarketChartData()
            
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
            val bitcoinJsonObject = jsonObject.getJSONObject("bitcoin")

            val pricesArray = bitcoinJsonObject.getJSONArray("prices")

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