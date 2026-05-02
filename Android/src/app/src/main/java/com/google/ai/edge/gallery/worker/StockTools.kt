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

package com.google.ai.edge.gallery.worker

import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

class StockTools(
    private val stockApiService: StockApiService,
    var apiKey: String = "",
    var apiSecret: String = ""
) : ToolSet {

    @Tool(description = "Get the current account status including cash and equity.")
    fun getAccountStatus(): Map<String, String> = runBlocking {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return@runBlocking mapOf("status" to "error", "message" to "API credentials not set")
        }
        try {
            val account = stockApiService.getAccount(apiKey, apiSecret)
            mapOf(
                "status" to "success",
                "cash" to account.cash,
                "equity" to account.equity,
                "portfolio_value" to account.portfolioValue
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the number of open orders.")
    fun getOrders(): Map<String, String> = runBlocking {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return@runBlocking mapOf("status" to "error", "message" to "API credentials not set")
        }
        try {
            val orders = stockApiService.getOrders(apiKey, apiSecret)
            mapOf("status" to "success", "count" to orders.size.toString())
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the latest stock price for a given symbol.")
    fun getStockPrice(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String
    ): Map<String, String> = runBlocking {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return@runBlocking mapOf("status" to "error", "message" to "API credentials not set")
        }
        try {
            val price = stockApiService.getStockPrice(apiKey, apiSecret, symbol)
            mapOf("status" to "success", "symbol" to symbol, "price" to price.toString())
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }
}
