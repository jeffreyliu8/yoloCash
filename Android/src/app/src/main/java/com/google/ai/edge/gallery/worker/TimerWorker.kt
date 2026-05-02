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
import android.os.Build
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
import com.google.ai.edge.gallery.data.room.ChatDao
import com.google.ai.edge.gallery.data.room.ChatHistory
import com.google.ai.edge.gallery.runtime.runtimeHelper
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.coroutines.resume

private const val TAG = "AGTimerWorker"
private const val MODEL_ALLOWLIST_FILENAME = "model_allowlist.json"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerWorkerEntryPoint {
    fun chatDao(): ChatDao
    fun dataStoreRepository(): DataStoreRepository
    fun json(): Json
}

class TimerWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationId = 1001
    private val channelId = "timer_worker_channel"

    override suspend fun doWork(): Result {
        return try {
            createNotificationChannel()

            val entryPoint = EntryPoints.get(applicationContext, TimerWorkerEntryPoint::class.java)
            val chatDao = entryPoint.chatDao()

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

            // 4. Send "hello"
            val prompt = "hello"
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

            // 5. Save to Room
            if (responseText.isNotEmpty()) {
                Log.d(TAG, "Received response: $responseText")
                chatDao.insertHistory(ChatHistory(prompt = prompt, response = responseText))
            }

            // Clean up
            suspendCancellableCoroutine { continuation ->
                model.runtimeHelper.cleanUp(model = model, onDone = { continuation.resume(Unit) })
            }

            // Counting from 1 to 5
            for (i in 1..5) {
                setForeground(createForegroundInfo(i))
                delay(1000) // 1 second delay between counts
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Timer Worker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Show counting progress"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(count: Int): ForegroundInfo {
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
            .setContentText("Count: $count")
            .setSmallIcon(R.drawable.logo)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
