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
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.proto.AlpacaCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StockAnalyzerUiState(
  val credentials: List<AlpacaCredential> = emptyList(),
  val watchlist: List<String> = emptyList(),
)

@HiltViewModel
class StockAnalyzerViewModel @Inject constructor(
  private val dataStoreRepository: DataStoreRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(StockAnalyzerUiState())
  val uiState: StateFlow<StockAnalyzerUiState> = _uiState.asStateFlow()

  init {
    loadData()
  }

  private fun loadData() {
    viewModelScope.launch {
      val credentials = dataStoreRepository.getAllAlpacaCredentials()
      val watchlist = dataStoreRepository.getWatchlist()
      _uiState.value = StockAnalyzerUiState(credentials, watchlist)
    }
  }

  fun addCredential(name: String, apiKey: String, apiSecret: String) {
    val credential = AlpacaCredential.newBuilder()
      .setName(name)
      .setApiKey(apiKey)
      .setApiSecret(apiSecret)
      .build()
    dataStoreRepository.addAlpacaCredential(credential)
    loadData()
  }

  fun deleteCredential(name: String) {
    dataStoreRepository.deleteAlpacaCredential(name)
    loadData()
  }

  fun addToWatchlist(symbol: String) {
    dataStoreRepository.addStockToWatchlist(symbol.uppercase())
    loadData()
  }

  fun removeFromWatchlist(symbol: String) {
    dataStoreRepository.removeStockFromWatchlist(symbol)
    loadData()
  }
}
