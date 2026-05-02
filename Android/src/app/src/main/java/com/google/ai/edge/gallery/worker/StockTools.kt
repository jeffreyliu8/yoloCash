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

    @Tool(description = "Calculate the MACD (Moving Average Convergence Divergence) for a stock symbol to help decide buy/sell.")
    fun getMACD(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String
    ): Map<String, String> = runBlocking {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return@runBlocking mapOf("status" to "error", "message" to "API credentials not set")
        }
        try {
            val bars = stockApiService.getBars(apiKey, apiSecret, symbol, limit = 100)
            if (bars.size < 34) {
                return@runBlocking mapOf("status" to "error", "message" to "Not enough data for MACD")
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
    fun placeOrder(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String,
        @ToolParam(description = "The quantity of shares to buy or sell.") qty: String,
        @ToolParam(description = "The side of the order: 'buy' or 'sell'.") side: String,
        @ToolParam(description = "The order type, default is 'market'.") type: String = "market"
    ): Map<String, String> = runBlocking {
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            return@runBlocking mapOf("status" to "error", "message" to "API credentials not set")
        }
        try {
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

    private fun calculateEMA(data: List<Double>, period: Int): List<Double> {
        if (data.size < period) return List(data.size) { 0.0 }
        val emaList = mutableListOf<Double>()
        val multiplier = 2.0 / (period + 1)
        
        var ema = data.take(period).average()
        for (i in 0 until period - 1) emaList.add(0.0)
        emaList.add(ema)
        
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
            emaList.add(ema)
        }
        return emaList
    }
}
