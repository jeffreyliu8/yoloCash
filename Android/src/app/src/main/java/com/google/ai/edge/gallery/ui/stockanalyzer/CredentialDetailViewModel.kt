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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.data.AlpacaAccount
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.gallery.data.room.WatchlistStockEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CredentialDetailUiState(
  val credentialName: String = "",
  val account: AlpacaAccount? = null,
  val watchlist: List<String> = emptyList(),
  val isLoading: Boolean = false,
  val error: String? = null,
)

@HiltViewModel
class CredentialDetailViewModel @Inject constructor(
  private val stockDao: StockDao,
  private val stockApiService: StockApiService,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val credentialName: String = checkNotNull(savedStateHandle["credentialName"])

  private val _accountInfo = MutableStateFlow<AlpacaAccount?>(null)
  private val _isLoading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)

  val uiState: StateFlow<CredentialDetailUiState> = combine(
    _accountInfo,
    stockDao.getWatchlist(),
    _isLoading,
    _error
  ) { account, watchlist, isLoading, error ->
    CredentialDetailUiState(
      credentialName = credentialName,
      account = account,
      watchlist = watchlist.map { it.symbol },
      isLoading = isLoading,
      error = error
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = CredentialDetailUiState(credentialName = credentialName, isLoading = true)
  )

  init {
    fetchAccountInfo()
  }

  fun fetchAccountInfo() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      try {
        val credentials = stockDao.getAllCredentials().first()
        val credential = credentials.find { it.name == credentialName }
        if (credential != null) {
          val account = stockApiService.getAccount(credential.apiKey, credential.apiSecret)
          _accountInfo.value = account
        } else {
          _error.value = "Credential not found"
        }
      } catch (e: Exception) {
        _error.value = "Failed to fetch account info: ${e.message}"
      } finally {
        _isLoading.value = false
      }
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
