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
import com.google.ai.edge.gallery.data.AlpacaOrder
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.room.StockDao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrderDetailUiState(
  val credentialName: String = "",
  val orderId: String = "",
  val order: AlpacaOrder? = null,
  val isLoading: Boolean = false,
  val isCancelling: Boolean = false,
  val error: String? = null,
  val message: String? = null,
)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
  private val stockDao: StockDao,
  private val stockApiService: StockApiService,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {

  private val credentialName: String = checkNotNull(savedStateHandle["credentialName"])
  private val orderId: String = checkNotNull(savedStateHandle["orderId"])

  private val _order = MutableStateFlow<AlpacaOrder?>(null)
  private val _isLoading = MutableStateFlow(false)
  private val _isCancelling = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)
  private val _message = MutableStateFlow<String?>(null)

  val uiState: StateFlow<OrderDetailUiState> = combine(
    _order,
    _isLoading,
    _isCancelling,
    _error,
    _message
  ) { order, isLoading, isCancelling, error, message ->
    OrderDetailUiState(
      credentialName = credentialName,
      orderId = orderId,
      order = order,
      isLoading = isLoading,
      isCancelling = isCancelling,
      error = error,
      message = message
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = OrderDetailUiState(credentialName = credentialName, orderId = orderId, isLoading = true)
  )

  init {
    fetchOrderDetails()
  }

  fun fetchOrderDetails() {
    viewModelScope.launch {
      _isLoading.value = true
      _error.value = null
      try {
        val credential = getCredential()
        if (credential != null) {
          val orders = stockApiService.getOrders(credential.apiKey, credential.apiSecret)
          val foundOrder = orders.find { it.id == orderId }
          if (foundOrder != null) {
            _order.value = foundOrder
          } else {
            _error.value = "Order not found"
          }
        } else {
          _error.value = "Credential not found"
        }
      } catch (e: Exception) {
        _error.value = "Failed to fetch order: ${e.message}"
      } finally {
        _isLoading.value = false
      }
    }
  }

  fun cancelOrder() {
    viewModelScope.launch {
      _isCancelling.value = true
      _error.value = null
      _message.value = null
      try {
        val credential = getCredential()
        if (credential != null) {
          stockApiService.deleteOrder(credential.apiKey, credential.apiSecret, orderId)
          _message.value = "Order cancelled successfully"
          fetchOrderDetails()
        } else {
          _error.value = "Credential not found"
        }
      } catch (e: Exception) {
        _error.value = "Failed to cancel order: ${e.message}"
      } finally {
        _isCancelling.value = false
      }
    }
  }

  private suspend fun getCredential() = stockDao.getAllCredentials().first().find { it.name == credentialName }
}
