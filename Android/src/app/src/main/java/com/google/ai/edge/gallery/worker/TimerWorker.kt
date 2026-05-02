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
import android.util.Log
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
import com.google.ai.edge.litertlm.tool
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "AGTimerWorker"
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
        Log.d(TAG, "[$header] $content")
        logDao?.insertLog(LogEntry(header = header, content = content))
        setForeground(createForegroundInfo("$header: $content"))
    }

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()
            setForeground(createForegroundInfo("Checking market status..."))
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

            setForeground(createForegroundInfo("Initializing model..."))
            val json = entryPoint.json()

            // Initialize StockTools
            val stockTools = StockTools(stockApiService)
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
                setForeground(createForegroundInfo("Processing ${credential.name}..."))
                
                // Update StockTools with current credentials
                stockTools.apiKey = credential.apiKey
                stockTools.apiSecret = credential.apiSecret
                
                model.runtimeHelper.resetConversation(model, tools = tools)
                
                val watchlist = stockDao.getWatchlist(credential.name).first()
                val symbols = watchlist.map { it.symbol }.joinToString(", ")

                val prompt = """
                    Analyze the Alpaca account '${credential.name}'.
                    Watchlist symbols: $symbols
                    
                    Tasks:
                    1. Check account status (cash, equity).
                    2. Check open orders.
                    3. For each symbol in the watchlist, check its current price and calculate MACD.
                    4. Based on the MACD (bullish/bearish) and your available cash, decide if any buy or sell orders should be placed.
                    5. If you decide to trade, use the placeOrder tool. Limit your trades to small quantities (e.g., 1 share) for now.
                    6. Provide a summary of your actions and reasoning.
                """.trimIndent()

                val inferenceResult = suspendCancellableCoroutine<kotlin.Result<String>> { continuation ->
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

                inferenceResult.fold(
                    onSuccess = { responseText ->
                        if (responseText.isNotEmpty()) {
                            logToBoth(
                                header = "Analysis for ${credential.name}",
                                content = responseText
                            )
                        }
                    },
                    onFailure = { error ->
                        logToBoth(
                            header = "Inference error",
                            content = error.message ?: "Unknown error"
                        )
                    }
                )
            }

            // Clean up
            setForeground(createForegroundInfo("Cleaning up..."))
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

    private suspend fun findGemma4Model(json: Json): Model? {
        try {
            val externalFilesDir = applicationContext.getExternalFilesDir(null)
            val file = File(externalFilesDir, MODEL_ALLOWLIST_FILENAME)
            if (!file.exists()) return null

            val content = file.readText()
            val allowlist = json.decodeFromString<ModelAllowlist>(content)

            // Look for a model that contains "Gemma 4" in its name
            val allowedModel =
                allowlist.models.find { it.name.contains("Gemma-4", ignoreCase = true) }
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
