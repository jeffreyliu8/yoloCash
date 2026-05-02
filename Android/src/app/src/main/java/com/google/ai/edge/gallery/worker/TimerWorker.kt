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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Stock Analyzer is running")
    }

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()
            setForeground(createForegroundInfo("Checking market status..."))
            val entryPoint = EntryPoints.get(applicationContext, TimerWorkerEntryPoint::class.java)
            val logDao = entryPoint.logDao()
            val stockDao = entryPoint.stockDao()
            val stockApiService = entryPoint.stockApiService()

            // Check if market is open
            val credentials = stockDao.getAllCredentials().first()
            if (credentials.isNotEmpty()) {
                val firstCredential = credentials[0]
                try {
                    val clock = stockApiService.getClock(firstCredential.apiKey, firstCredential.apiSecret)
                    if (!clock.isOpen) {
                        Log.d(TAG, "Market is closed. Stopping worker.")
                        setForeground(createForegroundInfo("Market is closed. Stopping."))
                        logDao.insertLog(
                            LogEntry(
                                header = "Market is closed. Stopping.",
                                content = "Market is closed. Stopping."
                            )
                        )
                        return Result.success()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to check market status", e)
                }
            }

            setForeground(createForegroundInfo("Initializing model..."))
            val json = entryPoint.json()

            // 1. Find Gemma 4 model
            val model = findGemma4Model(json)
            if (model == null) {
                Log.e(TAG, "Gemma 4 model not found in allowlist")
                return Result.failure()
            }

            // 2. Check if downloaded
            val modelFile = File(model.getPath(applicationContext))
            if (!modelFile.exists()) {
                Log.e(TAG, "Gemma 4 model not downloaded at ${modelFile.absolutePath}")
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
                    coroutineScope = null
                )
            }

            if (initializationError.isNotEmpty()) {
                Log.e(TAG, "Failed to initialize model: $initializationError")
                return Result.failure()
            }

            for (credential in credentials) {
                setForeground(createForegroundInfo("Processing ${credential.name}..."))
                model.runtimeHelper.resetConversation(model)
                //  Get Account Status
                val account = try {
                    stockApiService.getAccount(credential.apiKey, credential.apiSecret)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get account for ${credential.name}", e)
                    null
                }

                if (account != null) {
                    val prompt =
                        "Summarize the account status for ${credential.name}: Cash=${account.cash}, Equity=${account.equity}, Portfolio Value=${account.portfolioValue}. Keep it brief."
                    val responseText = suspendCancellableCoroutine { continuation ->
                        var fullResponse = ""
                        model.runtimeHelper.runInference(
                            model = model,
                            input = prompt,
                            resultListener = { partial, done, _ ->
                                fullResponse += partial
                                if (done) {
                                    continuation.resume(fullResponse)
                                }
                            },
                            cleanUpListener = {},
                            onError = { error ->
                                Log.e(TAG, "Inference error: $error")
                                continuation.resume("")
                            },
                            coroutineScope = null
                        )
                    }

                    // 7. Save to Room
                    if (responseText.isNotEmpty()) {
                        Log.d(TAG, "Received response for ${credential.name}: $responseText")
                        logDao.insertLog(
                            LogEntry(
                                header = "Summary for ${credential.name}",
                                content = responseText
                            )
                        )
                    }
                }
            }

            // Clean up
            setForeground(createForegroundInfo("Cleaning up..."))
            suspendCancellableCoroutine { continuation ->
                model.runtimeHelper.cleanUp(model = model, onDone = { continuation.resume(Unit) })
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "TimerWorker failed with exception", e)
            Result.failure()
        }
    }

    private fun findGemma4Model(json: Json): Model? {
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
            Log.e(TAG, "Error finding Gemma 4 model", e)
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
