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
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.AlpacaWSMessage
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.room.LogDao
import com.google.ai.edge.gallery.data.room.LogEntry
import com.google.ai.edge.gallery.data.room.StockDao
import com.orhanobut.logger.Logger
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class LiveService : Service() {

    @Inject
    lateinit var stockDao: StockDao

    @Inject
    lateinit var stockApiService: StockApiService

    @Inject
    lateinit var logDao: LogDao

    @Inject
    lateinit var httpClient: HttpClient

    @Inject
    lateinit var json: Json

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val notificationId = 1002
    private val channelId = "live_service_channel"

    companion object {
        const val ACTION_STOP = "STOP_LIVE_SERVICE"
        private const val TAG = "LiveService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification("Initializing news stream...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }

        scope.launch {
            observeNews()
        }

        return START_NOT_STICKY
    }

    private suspend fun observeNews() {
        val credentials = stockDao.getAllCredentials().firstOrNull()
        if (credentials.isNullOrEmpty()) {
            logToBoth("LiveService", "No Alpaca credentials found.")
            stopSelf()
            return
        }

        val credential = credentials.first()
        logToBoth("LiveService", "Connecting to news stream for ${credential.name}")

        while (job.isActive) {
            try {
                httpClient.webSocket(
                    method = HttpMethod.Get,
                    host = "stream.data.alpaca.markets",
                    path = "/v1beta1/news",
                    request = {
                        url {
                            protocol = URLProtocol.WSS
                        }
                    }
                ) {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            val messages = json.decodeFromString<List<AlpacaWSMessage>>(text)
                            for (msg in messages) {
                                when (msg.type) {
                                    "success" -> {
                                        if (msg.msg == "connected") {
                                            logToBoth("Alpaca News Stream", "connected!")
                                            sendSerialized(
                                                mapOf(
                                                    "action" to "auth",
                                                    "key" to credential.apiKey,
                                                    "secret" to credential.apiSecret
                                                )
                                            )
                                        } else if (msg.msg == "authenticated") {
                                            logToBoth("Alpaca News Stream", "authenticated!")
                                            @Serializable
                                            class ActionSubscribe(
                                                private val action: String,
                                                private val news: List<String>
                                            )

                                            val customer = ActionSubscribe(
                                                action = "subscribe",
                                                news = listOf("*")
                                            )
                                            sendSerialized(customer)
                                        }
                                    }

                                    "n" -> {
                                        logToBoth(
                                            "Alpaca News Stream",
                                            msg.headline ?: "No headline"
                                        )
                                    }

                                    "error" -> {
                                        Log.e(TAG, "Stream error: ${msg.msg}")
                                        logToBoth("Stream Error", msg.msg ?: "Unknown error")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebSocket error, reconnecting in 5s...", e)
                delay(5000)
            }
        }
    }

    private suspend fun logToBoth(header: String, content: String) {
        Logger.d("[$header] $content")
        logDao.insertLog(LogEntry(header = header, content = content))
        updateNotification(content)
    }

    private fun createNotification(content: String): android.app.Notification {
        val stopIntent = Intent(this, LiveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Live Alpaca News Stream")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_experiment)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", stopPendingIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Live Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
