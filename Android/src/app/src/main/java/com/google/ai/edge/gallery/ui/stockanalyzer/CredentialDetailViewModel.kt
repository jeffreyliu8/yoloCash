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
import com.google.ai.edge.gallery.data.AlpacaOrder
import com.google.ai.edge.gallery.data.AlpacaPosition
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.room.StockDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CredentialDetailUiState(
  val credentialName: String = "",
  val isEnabled: Boolean = true,
  val account: AlpacaAccount? = null,
  val orders: List<AlpacaOrder> = emptyList(),
  val positions: List<AlpacaPosition> = emptyList(),
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

  private val _isEnabled = MutableStateFlow(true)
  private val _accountInfo = MutableStateFlow<AlpacaAccount?>(null)
  private val _orders = MutableStateFlow<List<AlpacaOrder>>(emptyList())
  private val _positions = MutableStateFlow<List<AlpacaPosition>>(emptyList())
  private val _isLoading = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)

  val uiState: StateFlow<CredentialDetailUiState> = combine(
    _isEnabled,
    _accountInfo,
    _orders,
    _positions,
    _isLoading,
    _error
  ) { flows: Array<Any?> ->
    CredentialDetailUiState(
      credentialName = credentialName,
      isEnabled = flows[0] as Boolean,
      account = flows[1] as AlpacaAccount?,
      orders = flows[2] as List<AlpacaOrder>,
      positions = flows[3] as List<AlpacaPosition>,
      isLoading = flows[4] as Boolean,
      error = flows[5] as String?
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
        val credential = getCredential()
        if (credential != null) {
          _isEnabled.value = credential.enabled
          val accountDeferred = async { stockApiService.getAccount(credential.apiKey, credential.apiSecret) }
          val ordersDeferred = async { stockApiService.getOrders(credential.apiKey, credential.apiSecret) }
          val positionsDeferred = async { stockApiService.getPositions(credential.apiKey, credential.apiSecret) }

          _accountInfo.value = accountDeferred.await()
          _orders.value = ordersDeferred.await()
          _positions.value = positionsDeferred.await()
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

  fun toggleEnabled(enabled: Boolean) {
    viewModelScope.launch {
      val credential = getCredential()
      if (credential != null) {
        val updatedCredential = credential.copy(enabled = enabled)
        stockDao.insertCredential(updatedCredential)
        _isEnabled.value = enabled
      }
    }
  }

  suspend fun getCredential() = stockDao.getAllCredentials().first().find { it.name == credentialName }

  fun getStockApiService() = stockApiService
}
