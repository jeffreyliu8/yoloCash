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

package com.google.ai.edge.gallery.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.RuntimeType
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.common.modelitem.ModelItem
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySettingsScreen(
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelSelected: (Task, Model) -> Unit,
  onBenchmarkClicked: (Model) -> Unit,
  onStockAnalyzerClicked: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val downloadedModels = remember(uiState.modelDownloadStatus) {
    viewModel.getAllDownloadedModels()
  }
  
  val scope = rememberCoroutineScope()
  val modelItemExpandedStates = remember { mutableStateMapOf<String, Boolean>() }

  val handleClickModel: (Model) -> Unit = { model ->
    onStockAnalyzerClicked()
  }

  // Handle system's edge swipe.
  BackHandler { navigateUp() }

  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Icon(
                Icons.Rounded.Settings,
                modifier = Modifier.size(20.dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.my_settings),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(onClick = { navigateUp() }) {
            Icon(
              imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
              contentDescription = stringResource(R.string.cd_navigate_back_icon),
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }
        },
        modifier = modifier,
      )
    },
  ) { innerPadding ->
    Box() {
      if (downloadedModels.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = innerPadding.calculateTopPadding() + 32.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No models downloaded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      } else {
        LazyColumn(
          modifier =
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(top = innerPadding.calculateTopPadding()),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding =
            PaddingValues(top = 16.dp, bottom = innerPadding.calculateBottomPadding() + 80.dp),
        ) {
          items(downloadedModels) { model ->
            val expanded = modelItemExpandedStates.getOrDefault(model.name, true)
            ModelItem(
              model = model,
              task = null,
              modelManagerViewModel = viewModel,
              onModelClicked = handleClickModel,
              onBenchmarkClicked = onBenchmarkClicked,
              expanded = expanded,
              showBenchmarkButton = model.runtimeType == RuntimeType.LITERT_LM,
              onExpanded = { modelItemExpandedStates[model.name] = it },
            )
          }
        }
      }

      // Gradient overlay at the bottom.
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(innerPadding.calculateBottomPadding())
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surfaceContainer)
              )
            )
            .align(Alignment.BottomCenter)
      )
    }
  }
}
