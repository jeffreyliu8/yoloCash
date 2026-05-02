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

package com.google.ai.edge.gallery.ui.stockanalyzer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.room.AlpacaCredentialEntity
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.gallery.worker.TimerWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StockAnalyzerSettingsViewModel @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
  private val stockDao: StockDao,
  @ApplicationContext private val context: Context,
) : ViewModel() {

  private val _isTimerEnabled = MutableStateFlow(dataStoreRepository.isTimerWorkerEnabled())
  val isTimerEnabled = _isTimerEnabled.asStateFlow()

  private val _isDebugModeEnabled = MutableStateFlow(dataStoreRepository.isDebugModeEnabled())
  val isDebugModeEnabled = _isDebugModeEnabled.asStateFlow()

  private val workManager = WorkManager.getInstance(context)

  fun toggleTimer(enabled: Boolean) {
    dataStoreRepository.setTimerWorkerEnabled(enabled)
    _isTimerEnabled.value = enabled
    if (enabled) {
      val workRequest = PeriodicWorkRequestBuilder<TimerWorker>(15, TimeUnit.MINUTES)
        .build()
      workManager.enqueueUniquePeriodicWork(
        "TimerWorker",
        ExistingPeriodicWorkPolicy.KEEP,
        workRequest
      )
    } else {
      workManager.cancelUniqueWork("TimerWorker")
    }
  }

  fun toggleDebugMode(enabled: Boolean) {
    dataStoreRepository.setDebugModeEnabled(enabled)
    _isDebugModeEnabled.value = enabled
  }

  fun triggerImmediateTimer() {
    val workRequest = OneTimeWorkRequestBuilder<TimerWorker>().build()
    workManager.enqueueUniqueWork(
      "TimerWorkerImmediate",
      ExistingWorkPolicy.REPLACE,
      workRequest
    )
  }

  fun populateCredentials() {
    viewModelScope.launch {
      stockDao.insertCredential(
        AlpacaCredentialEntity(
          name = "Account 1",
          apiKey = "PKMN74Y7PMLTGKIGBKL4VV7DUF",
          apiSecret = "CCyTsMJ8W4kQi1zysW57Ga1CUen4Q27mNDD9MRR1cu1C"
        )
      )
      stockDao.insertCredential(
        AlpacaCredentialEntity(
          name = "Account 2",
          apiKey = "PK3SIFRQMOJLSE443UBGHOZSCY",
          apiSecret = "DF5QYtvT4dTDM5Mva3yFrYf2c4TYhhaSkifaTBkEsTo4"
        )
      )
      stockDao.insertCredential(
        AlpacaCredentialEntity(
          name = "Account 3",
          apiKey = "PKGETVNBNXWAY44G7JOHKT3BYV",
          apiSecret = "ETw7JGPuLP5f8eLbFUXnJutiN7d5qcEEZNEozWWaVK5u"
        )
      )
    }
  }
}
