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
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

class StockTools(
    private val stockApiService: StockApiService,
    private val stockDao: StockDao
) : ToolSet {

    private suspend fun getApiKeys(credential: String): Pair<String, String>? {
        val entity = stockDao.getCredential(credential)
        return if (entity != null) entity.apiKey to entity.apiSecret else null
    }

    @Tool(description = "Get the current account status including cash and equity.")
    suspend fun getAccountStatus(
        @ToolParam(description = "The Alpaca credential name.") credential: String
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
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
    suspend fun getOrders(
        @ToolParam(description = "The Alpaca credential name.") credential: String
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            val orders = stockApiService.getOrders(apiKey, apiSecret)
            mapOf("status" to "success", "count" to orders.size.toString())
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the latest stock price for a given symbol.")
    suspend fun getStockPrice(
        @ToolParam(description = "The Alpaca credential name.") credential: String,
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            val price = stockApiService.getStockPrice(apiKey, apiSecret, symbol)
            mapOf("status" to "success", "symbol" to symbol, "price" to price.toString())
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Calculate the MACD (Moving Average Convergence Divergence) for a stock symbol to help decide buy/sell.")
    suspend fun getMACD(
        @ToolParam(description = "The Alpaca credential name.") credential: String,
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            val bars = stockApiService.getBars(apiKey, apiSecret, symbol, limit = 100)
            if (bars.size < 34) {
                return mapOf("status" to "error", "message" to "Not enough data for MACD")
            }
            val closes = bars.map { it.close }
            val ema12 = calculateEMA(closes, 12)
            val ema26 = calculateEMA(closes, 26)
            
            val macdLine = ema12.zip(ema26).map { (e12, e26) -> e12 - e26 }
            // MACD line is valid only from index 25 onwards (because of EMA 26)
            val validMacdLine = macdLine.drop(25)
            val signalLine = calculateEMA(validMacdLine, 9)
            
            val latestMacd = validMacdLine.last()
            val latestSignal = signalLine.last()
            val histogram = latestMacd - latestSignal
            
            mapOf(
                "status" to "success",
                "symbol" to symbol,
                "macd" to latestMacd.toString(),
                "signal" to latestSignal.toString(),
                "histogram" to histogram.toString(),
                "advice" to if (histogram > 0) "bullish" else "bearish"
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Place a buy or sell order for a stock.")
    suspend fun placeOrder(
        @ToolParam(description = "The Alpaca credential name.") credential: String,
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String,
        @ToolParam(description = "The quantity of shares to buy or sell.") qty: String,
        @ToolParam(description = "The side of the order: 'buy' or 'sell'.") side: String,
        @ToolParam(description = "The order type, default is 'market'.") type: String = "market"
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            val order = stockApiService.postOrder(apiKey, apiSecret, symbol, qty, side, type)
            mapOf(
                "status" to "success",
                "order_id" to order.id,
                "symbol" to order.symbol,
                "side" to order.side,
                "qty" to (order.qty ?: "0"),
                "order_status" to order.status
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Cancel an open order by its ID.")
    suspend fun cancelOrder(
        @ToolParam(description = "The Alpaca credential name.") credential: String,
        @ToolParam(description = "The ID of the order to cancel.") orderId: String
    ): Map<String, String> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            stockApiService.deleteOrder(apiKey, apiSecret, orderId)
            mapOf("status" to "success", "message" to "Order $orderId cancelled")
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the latest news for specific stock symbols or general market news.")
    suspend fun getLatestNews(
        @ToolParam(description = "The Alpaca credential name.") credential: String,
        @ToolParam(description = "Comma-separated stock symbols, e.g., 'AAPL,TSLA'. If omitted, general market news is returned.") symbols: String? = null,
        @ToolParam(description = "The number of news items to return, default is 5.") limit: Int = 5
    ): Map<String, Any> {
        val (apiKey, apiSecret) = getApiKeys(credential) ?: return mapOf(
            "status" to "error",
            "message" to "Credential '$credential' not found"
        )
        return try {
            val news = stockApiService.getLatestNews(apiKey, apiSecret, symbols, limit)
            mapOf(
                "status" to "success",
                "news" to news.map {
                    mapOf(
                        "headline" to it.headline,
                        "summary" to it.summary,
                        "created_at" to it.createdAt,
                        "symbols" to it.symbols
                    )
                }
            )
        } catch (e: Exception) {
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    private fun calculateEMA(data: List<Double>, period: Int): List<Double> {
        if (data.size < period) return List(data.size) { 0.0 }
        val emaList = mutableListOf<Double>()
        val multiplier = 2.0 / (period + 1)
        
        var ema = data.take(period).average()
        for (i in 0 until period - 1) emaList.add(0.0)
        emaList.add(ema)
        
        for (i in period until data.size) {
            ema += (data[i] - ema) * multiplier
            emaList.add(ema)
        }
        return emaList
    }
}
