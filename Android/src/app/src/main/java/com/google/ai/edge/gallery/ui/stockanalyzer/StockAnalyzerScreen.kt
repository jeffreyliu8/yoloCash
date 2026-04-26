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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.room.AlpacaCredentialEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAnalyzerScreen(
  onBackClicked: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: StockAnalyzerViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  var selectedTabIndex by remember { mutableStateOf(0) }
  val tabs = listOf(stringResource(R.string.credentials), stringResource(R.string.watchlist))

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Stock Analyzer") },
        navigationIcon = {
          IconButton(onClick = onBackClicked) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back_icon),
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      TabRow(selectedTabIndex = selectedTabIndex) {
        tabs.forEachIndexed { index, title ->
          Tab(
            selected = selectedTabIndex == index,
            onClick = { selectedTabIndex = index },
            text = { Text(title) }
          )
        }
      }

      when (selectedTabIndex) {
        0 -> CredentialsTab(
          credentials = uiState.credentials,
          onAddCredential = { name, key, secret -> viewModel.addCredential(name, key, secret) },
          onDeleteCredential = { name -> viewModel.deleteCredential(name) }
        )
        1 -> WatchlistTab(
          watchlist = uiState.watchlist,
          onAddStock = { symbol -> viewModel.addToWatchlist(symbol) },
          onRemoveStock = { symbol -> viewModel.removeFromWatchlist(symbol) }
        )
      }
    }
  }
}

@Composable
fun CredentialsTab(
  credentials: List<AlpacaCredentialEntity>,
  onAddCredential: (String, String, String) -> Unit,
  onDeleteCredential: (String) -> Unit,
) {
  var name by remember { mutableStateOf("") }
  var apiKey by remember { mutableStateOf("") }
  var apiSecret by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("Add New Credential", style = MaterialTheme.typography.titleMedium)
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.api_key)) },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = apiSecret,
            onValueChange = { apiSecret = it },
            label = { Text(stringResource(R.string.api_secret)) },
            modifier = Modifier.fillMaxWidth()
          )
          Button(
            onClick = {
              if (name.isNotBlank() && apiKey.isNotBlank() && apiSecret.isNotBlank()) {
                onAddCredential(name, apiKey, apiSecret)
                name = ""
                apiKey = ""
                apiSecret = ""
              }
            },
            modifier = Modifier.align(Alignment.End)
          ) {
            Text(stringResource(R.string.add))
          }
        }
      }
    }

    item {
      HorizontalDivider()
    }

    items(credentials) { credential ->
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Row(
          modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(credential.name, fontWeight = FontWeight.Bold)
            Text("Key: ${credential.apiKey.take(4)}...", style = MaterialTheme.typography.bodySmall)
          }
          IconButton(onClick = { onDeleteCredential(credential.name) }) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
          }
        }
      }
    }
  }
}

@Composable
fun WatchlistTab(
  watchlist: List<String>,
  onAddStock: (String) -> Unit,
  onRemoveStock: (String) -> Unit,
) {
  var symbol by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      OutlinedTextField(
        value = symbol,
        onValueChange = { symbol = it },
        label = { Text(stringResource(R.string.stock_symbol)) },
        modifier = Modifier.weight(1f)
      )
      Button(onClick = {
        if (symbol.isNotBlank()) {
          onAddStock(symbol)
          symbol = ""
        }
      }) {
        Text(stringResource(R.string.add))
      }
    }

    HorizontalDivider()

    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(watchlist) { item ->
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
          Row(
            modifier = Modifier
              .padding(16.dp)
              .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(item, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { onRemoveStock(item) }) {
              Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
          }
        }
      }
    }
  }
}
