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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StockAnalyzerUiState(
    val credentials: List<AlpacaCredentialEntity> = emptyList(),
)

@HiltViewModel
class StockAnalyzerViewModel @Inject constructor(
    stockDao: StockDao,
) : ViewModel() {

    val uiState: StateFlow<StockAnalyzerUiState> = stockDao.getAllCredentials()
        .map { credentials ->
            StockAnalyzerUiState(credentials = credentials)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StockAnalyzerUiState()
        )
}
