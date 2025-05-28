package com.lennardrischen.bitcoinwidgets

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient

class BitcoinPriceData(

)

class RefreshPriceWidgetWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    companion object {
        const val TAG = "RefreshPriceWidgetWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "onWork invoked.")

        return try {
            val bitcoinPriceData = ApiRequester.fetchBitcoinPriceData()
            
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

        fun fetchBitcoinPriceData(): BitcoinPriceData {
            return BitcoinPriceData()
        }
    }
}