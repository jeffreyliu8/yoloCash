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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.room.AlpacaCredentialEntity
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.gallery.data.room.WatchlistStockEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StockAnalyzerUiState(
  val credentials: List<AlpacaCredentialEntity> = emptyList(),
  val watchlist: List<String> = emptyList(),
)

@HiltViewModel
class StockAnalyzerViewModel @Inject constructor(
  private val stockDao: StockDao,
) : ViewModel() {

  val uiState: StateFlow<StockAnalyzerUiState> = combine(
    stockDao.getAllCredentials(),
    stockDao.getWatchlist()
  ) { credentials, watchlist ->
    StockAnalyzerUiState(
      credentials = credentials,
      watchlist = watchlist.map { it.symbol }
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = StockAnalyzerUiState()
  )

  fun addCredential(name: String, apiKey: String, apiSecret: String) {
    viewModelScope.launch {
      stockDao.insertCredential(
        AlpacaCredentialEntity(name, apiKey, apiSecret)
      )
    }
  }

  fun deleteCredential(name: String) {
    viewModelScope.launch {
      stockDao.deleteCredential(name)
    }
  }

  fun addToWatchlist(symbol: String) {
    viewModelScope.launch {
      stockDao.insertStock(WatchlistStockEntity(symbol.uppercase()))
    }
  }

  fun removeFromWatchlist(symbol: String) {
    viewModelScope.launch {
      stockDao.deleteStock(symbol)
    }
  }
}
