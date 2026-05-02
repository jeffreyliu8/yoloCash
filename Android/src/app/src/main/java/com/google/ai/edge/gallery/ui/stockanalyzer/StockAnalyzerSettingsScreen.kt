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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockAnalyzerSettingsScreen(
  onBackClicked: () -> Unit,
  viewModel: StockAnalyzerSettingsViewModel = hiltViewModel()
) {
  val isTimerEnabled by viewModel.isTimerEnabled.collectAsState()

  Scaffold(
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text("Settings") },
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text("15 Minute Timer", style = MaterialTheme.typography.titleMedium)
          Text(
            "Execute a background worker every 15 minutes that shows a counting notification.",
            style = MaterialTheme.typography.bodySmall
          )
        }
        Switch(
          checked = isTimerEnabled,
          onCheckedChange = { viewModel.toggleTimer(it) }
        )
      }

      HorizontalDivider()

      Button(
        onClick = { viewModel.triggerImmediateTimer() },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Trigger Timer Now")
      }

      HorizontalDivider()

      Button(
        onClick = { viewModel.populateCredentials() },
        modifier = Modifier.fillMaxWidth()
      ) {
        Text("Populate Credentials")
      }
    }
  }
}
