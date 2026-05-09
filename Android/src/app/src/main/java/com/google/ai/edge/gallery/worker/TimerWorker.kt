/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.ModelAllowlist
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.room.LogDao
import com.google.ai.edge.gallery.data.room.LogEntry
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.gallery.runtime.runtimeHelper
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.tool
import com.orhanobut.logger.Logger
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume

private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"


@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerWorkerEntryPoint {
    fun logDao(): LogDao
    fun dataStoreRepository(): DataStoreRepository
    fun json(): Json
    fun stockDao(): StockDao
    fun stockApiService(): StockApiService
}

class TimerWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationId = 1001
    private val channelId = "timer_worker_channel"
    private var logDao: LogDao? = null

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Stock Analyzer is running")
    }

    private suspend fun logToBoth(header: String, content: String) {
        Logger.d("[$header] $content")
        logDao?.insertLog(LogEntry(header = header, content = content))
        setForeground(createForegroundInfo("$header: $content"))
    }

    override suspend fun doWork(): Result {
        createNotificationChannel()
        setForeground(getForegroundInfo())
        return try {
            val entryPoint = EntryPoints.get(applicationContext, TimerWorkerEntryPoint::class.java)
            logDao = entryPoint.logDao()
            val stockDao = entryPoint.stockDao()
            val stockApiService = entryPoint.stockApiService()
            val dataStoreRepository = entryPoint.dataStoreRepository()

            logToBoth(
                header = "Debug mode check",
                content = "is debug mode: ${dataStoreRepository.isDebugModeEnabled()}"
            )

            // Check if market is open
            val credentials = stockDao.getAllCredentials().first()
            if (credentials.isNotEmpty() && !dataStoreRepository.isDebugModeEnabled()) {
                val firstCredential = credentials[0]
                try {
                    val clock =
                        stockApiService.getClock(firstCredential.apiKey, firstCredential.apiSecret)
                    if (!clock.isOpen) {
                        logToBoth(
                            header = "Market is closed. Stopping.",
                            content = "Market is closed. Stopping."
                        )
                        return Result.success()
                    }
                } catch (e: Exception) {
                    logToBoth(
                        header = "Market check error",
                        content = e.message ?: "Failed to check market status"
                    )
                }
            }

            logToBoth(
                header = "Initializing model...",
                content = "Initializing model..."
            )
            val json = entryPoint.json()

            // Initialize StockTools
            val stockTools = StockTools(stockApiService, currentCoroutineContext())
            val tools = listOf(tool(stockTools))

            // 1. Find Gemma 4 model
            val model = findGemma4Model(json)
            if (model == null) {
                logToBoth(header = "Model error", content = "Gemma 4 model not found in allowlist")
                return Result.failure()
            }

            // 2. Check if downloaded
            val modelFile = File(model.getPath(applicationContext))
            if (!modelFile.exists()) {
                logToBoth(
                    header = "Model error",
                    content = "Gemma 4 model not downloaded at ${modelFile.absolutePath}"
                )
                return Result.failure()
            }

            // 3. Initialize model
            val initializationError = suspendCancellableCoroutine { continuation ->
                model.runtimeHelper.initialize(
                    context = applicationContext,
                    model = model,
                    supportImage = false,
                    supportAudio = false,
                    onDone = { error -> continuation.resume(error) },
                    coroutineScope = null,
                    tools = tools
                )
            }

            if (initializationError.isNotEmpty()) {
                logToBoth(
                    header = "Initialization error",
                    content = "Failed to initialize model: $initializationError"
                )
                return Result.failure()
            }

            for (credential in credentials) {
                if (!credential.enabled) {
                    logToBoth(
                        header = "Skipping ${credential.name}",
                        content = "Credential ${credential.name} is disabled. Skipping."
                    )
                    continue
                }

                logToBoth(
                    header = "Processing ${credential.name}...",
                    content = "Processing ${credential.name}..."
                )

                // Update StockTools with current credentials
                stockTools.apiKey = credential.apiKey
                stockTools.apiSecret = credential.apiSecret


//                val watchlist = stockDao.getWatchlist(credential.name).first()
//                val symbols = watchlist.joinToString(", ") { it.symbol }
//

                // find out the top gain movers,
                val topMovers =
                    stockApiService.getTopMovers(credential.apiKey, credential.apiSecret, top = 20)
                val topGainers = topMovers.gainers.take(20)
                logToBoth(
                    header = "Top Gainers",
                    content = topGainers.joinToString(", ") { "${it.symbol} (${it.percentChange}%)" }
                )

                // find out the getMostActiveStocks by volume
                val mostActive = stockApiService.getMostActiveStocks(
                    credential.apiKey,
                    credential.apiSecret,
                    by = "volume",
                    top = 20
                )
                val topMostActive = mostActive.mostActives.take(20)
                logToBoth(
                    header = "Most Active Stocks",
                    content = topMostActive.joinToString(", ") { "${it.symbol} (vol: ${it.volume})" }
                )

                // find overlapping list
                val overlappingSymbols = topGainers.map { it.symbol }.intersect(
                    topMostActive.map { it.symbol }.toSet()
                )
                logToBoth(
                    header = "Overlapping Stocks",
                    content = if (overlappingSymbols.isEmpty()) {
                        "No overlapping stocks found between top gainers and most active."
                    } else {
                        "Overlapping symbols: ${overlappingSymbols.joinToString(", ")}"
                    }
                )
                if (overlappingSymbols.isEmpty()) {
                    continue
                }

                // iterate through every overlapping symbols, get the news and check if there is any positive news in the last 15 minutes
                val positiveOverlaps = mutableListOf<String>()
                for (symbol in overlappingSymbols) {
                    val recentNews = stockApiService.getLatestNews(
                        credential.apiKey,
                        credential.apiSecret,
                        symbols = symbol,
                        limit = 50
                    )

                    if (recentNews.isNotEmpty()) {
                        val headlines = recentNews.joinToString("\n") { "- ${it.headline}" }
                        val sentimentResponse = runStep(
                            credentialName = credential.name,
                            tools = tools,
                            model = model,
                            prompt = "Analyze the following news headlines for $symbol and determine if there is any positive sentiment that would justify a trade. Answer only 'YES' or 'NO'.\n\n$headlines",
                            header = "Sentiment Check: $symbol"
                        )
                        if (sentimentResponse.contains("YES", ignoreCase = true)) {
                            positiveOverlaps.add(symbol)
                        }
                    } else {
                        logToBoth(
                            header = "No news found in past 15 mins",
                            content = "No news found in past 15 mins"
                        )
                    }
                }

                logToBoth(
                    header = "Positive Overlap Stocks",
                    content = if (positiveOverlaps.isEmpty()) {
                        "No stocks with positive news in the last 15 minutes."
                    } else {
                        "Stocks with positive news: ${positiveOverlaps.joinToString(", ")}"
                    }
                )

                // check Account Status
                runStep(
                    credentialName = credential.name,
                    tools = tools,
                    model = model,
                    "Get the current account status for '${credential.name}'. Check our available buying power.",
                    "Account Status Step",
                )

                // Execute Momentum Trade
                runStep(
                    credentialName = credential.name,
                    tools = tools,
                    model = model,
                    "Use placeOrder to execute a large trade on top gainer which have positive news, just pick the first one. Use my account status to calculate how many orders I can buy.",
                    "Execute Trade Step",
                )
            }

            // Clean up
            logToBoth(
                header = "Cleaning up...",
                content = "Cleaning up..."
            )
            suspendCancellableCoroutine { continuation ->
                model.runtimeHelper.cleanUp(model = model, onDone = { continuation.resume(Unit) })
            }

            Result.success()
        } catch (e: Exception) {
            logToBoth(
                header = "Worker exception",
                content = e.message ?: "TimerWorker failed with exception"
            )
            Result.failure()
        }
    }

    private suspend fun runStep(
        credentialName: String,
        tools: List<ToolProvider>,
        model: Model,
        prompt: String,
        header: String,
    ): String {
        logToBoth(header = "$credentialName Request: $header", content = prompt)
        model.runtimeHelper.resetConversation(model, tools = tools)
        val result = suspendCancellableCoroutine { continuation ->
            var fullResponse = ""
            model.runtimeHelper.runInference(
                model = model,
                input = prompt,
                resultListener = { partial, done, _ ->
                    fullResponse += partial
                    if (done) {
                        continuation.resume(kotlin.Result.success(fullResponse.trim()))
                    }
                },
                cleanUpListener = {},
                onError = { error ->
                    continuation.resume(kotlin.Result.failure(Exception(error)))
                },
                coroutineScope = null
            )
        }

        return result.fold(
            onSuccess = { responseText ->
                logToBoth(header = "$credentialName Response: $header", content = responseText)
                responseText
            },
            onFailure = { error ->
                val errorMessage = error.message ?: "Unknown error"
                logToBoth(header = "$credentialName Error: $header", content = errorMessage)
                "Error: $errorMessage"
            }
        )
    }

    private suspend fun findGemma4Model(json: Json): Model? {
        try {
            val externalFilesDir = applicationContext.getExternalFilesDir(null)
            val file = File(externalFilesDir, MODEL_ALLOWLIST_FILENAME)
            if (!file.exists()) return null

            val content = file.readText()
            val allowlist = json.decodeFromString<ModelAllowlist>(content)

            // Look for a model that contains "Gemma 4" in its name
            val allowedModel =
                allowlist.models.find { it.name.contains("Gemma-4-E4B-it", ignoreCase = true) }
            return allowedModel?.toModel()?.apply { preProcess() }
        } catch (e: Exception) {
            logToBoth(
                header = "Model discovery error",
                content = e.message ?: "Error finding Gemma 4 model"
            )
            return null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Timer Worker",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "createNotificationChannel"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(msg: String): ForegroundInfo {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Timer Worker Running")
            .setContentText(msg)
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
