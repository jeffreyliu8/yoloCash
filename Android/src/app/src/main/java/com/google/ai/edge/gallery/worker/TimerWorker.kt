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
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.ai.edge.gallery.MainActivity
import kotlinx.coroutines.delay

class TimerWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private val notificationId = 1001
  private val channelId = "timer_worker_channel"

  override suspend fun doWork(): Result {
    createNotificationChannel()
    
    // Counting from 1 to 5
    for (i in 1..5) {
      setForeground(createForegroundInfo(i))
      delay(1000) // 1 second delay between counts
    }
    
    return Result.success()
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
      .setSmallIcon(android.R.drawable.ic_dialog_info)
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
