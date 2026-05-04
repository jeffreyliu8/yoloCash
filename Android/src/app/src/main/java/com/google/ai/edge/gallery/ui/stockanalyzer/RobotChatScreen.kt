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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatScreen
import com.google.ai.edge.gallery.ui.llmchat.LlmChatViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.ai.edge.gallery.worker.StockTools
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RobotChatScreen(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
    modelManagerViewModel: ModelManagerViewModel,
    credentialDetailViewModel: CredentialDetailViewModel = hiltViewModel(),
    chatViewModel: LlmChatViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val credentialUiState by credentialDetailViewModel.uiState.collectAsState()
    val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
    val selectedModel = modelManagerUiState.selectedModel
    val scope = rememberCoroutineScope()

    // Function to reset session with StockTools
    val resetSessionWithTools = { task: Task, model: Model ->
        scope.launch(Dispatchers.Default) {
            // Wait for model to be initialized
            while (model.instance == null) {
                delay(1200)
            }
            
            val credential = credentialDetailViewModel.getCredential()
            val tools = if (credential != null) {
                val stockTools = StockTools(
                    stockApiService = credentialDetailViewModel.getStockApiService(),
                    coroutineContext = Dispatchers.Default,
                    apiKey = credential.apiKey,
                    apiSecret = credential.apiSecret
                )
                listOf(tool(stockTools))
            } else {
                emptyList()
            }

            val systemInstruction = if (credential != null) {
                Contents.of("You are a helpful robot financial assistant for the Alpaca account '${credential.name}'. " +
                        "You have tools to check account status, current positions, orders, stock prices, and news. " +
                        "Use them to provide accurate information to the user.")
            } else null

            chatViewModel.resetSession(
                task = task,
                model = model,
                tools = tools,
                systemInstruction = systemInstruction,
                supportImage = false,
                supportAudio = false
            )
        }
    }

    // 1. Automatically select a Gemma-4 model if available and not already selected
    LaunchedEffect(Unit) {
        val allModels = modelManagerViewModel.getAllModels()
        val gemma4 = allModels.find { it.name.contains("Gemma-4", ignoreCase = true) }
        if (gemma4 != null && selectedModel.name != gemma4.name) {
            modelManagerViewModel.selectModel(gemma4)
        }
    }

    // 2. Initialize model and session with tools when model changes
    LaunchedEffect(selectedModel.name) {
        if (selectedModel.name.isNotEmpty()) {
            val task = modelManagerViewModel.getTaskById(BuiltInTaskId.LLM_CHAT)
            if (task != null) {
                modelManagerViewModel.initializeModel(context, task, selectedModel)
                resetSessionWithTools(task, selectedModel)
            }
        }
    }

    LlmChatScreen(
        modelManagerViewModel = modelManagerViewModel,
        taskId = BuiltInTaskId.LLM_CHAT,
        navigateUp = onBackClicked,
        viewModel = chatViewModel,
        onResetSessionClickedOverride = { task, model ->
            resetSessionWithTools(task, model)
        }
    )
}
