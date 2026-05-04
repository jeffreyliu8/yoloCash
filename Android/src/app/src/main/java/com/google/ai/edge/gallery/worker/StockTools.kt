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

import android.util.Log
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.coroutines.CoroutineContext

private const val TAG = "StockTools"

class StockTools(
    private val stockApiService: StockApiService,
    private val coroutineContext: CoroutineContext,
    var apiKey: String = "",
    var apiSecret: String = ""
) : ToolSet {

    @Tool(description = "Get the current account status including cash and equity.")
    fun getAccountStatus(): Map<String, String> = runBlocking(coroutineContext) {
        Log.d(TAG, "getAccountStatus() called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getAccountStatus error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val account = stockApiService.getAccount(apiKey, apiSecret)
            val result = mapOf(
                "status" to "success",
                "cash" to account.cash,
                "equity" to account.equity,
                "portfolio_value" to account.portfolioValue
            )
            Log.d(TAG, "getAccountStatus result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getAccountStatus error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the number of open orders.")
    fun getOrders(): Map<String, String> = runBlocking(coroutineContext) {
        Log.d(TAG, "getOrders() called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getOrders error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val orders = stockApiService.getOrders(apiKey, apiSecret)
            val result = mapOf("status" to "success", "count" to orders.size.toString())
            Log.d(TAG, "getOrders result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getOrders error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the current positions in the portfolio.")
    fun getPositions(): Map<String, Any> = runBlocking(coroutineContext) {
        Log.d(TAG, "getPositions() called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getPositions error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val positions = stockApiService.getPositions(apiKey, apiSecret)
            val result = mapOf(
                "status" to "success",
                "positions" to positions.map {
                    mapOf(
                        "symbol" to it.symbol,
                        "qty" to it.qty,
                        "market_value" to it.marketValue,
                        "current_price" to it.currentPrice,
                        "unrealized_pl" to it.unrealizedPl,
                        "change_today" to it.changeToday
                    )
                }
            )
            Log.d(TAG, "getPositions result: success (${positions.size} positions)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getPositions error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the latest stock price for a given symbol.")
    fun getStockPrice(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String
    ): Map<String, String> = runBlocking(coroutineContext) {
        Log.d(TAG, "getStockPrice(symbol=$symbol) called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getStockPrice error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val price = stockApiService.getStockPrice(apiKey, apiSecret, symbol)
            val result =
                mapOf("status" to "success", "symbol" to symbol, "price" to price.toString())
            Log.d(TAG, "getStockPrice result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getStockPrice error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Calculate the MACD (Moving Average Convergence Divergence) for a stock symbol to help decide buy/sell.")
    fun getMACD(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String,
        @ToolParam(description = "The timeframe for each bar, e.g., '1Min', '5Min', '15Min', '1Day'. Default is '1Min'.") timeframe: String = "1Min"
    ): Map<String, Any> = runBlocking(coroutineContext) {
        Log.d(TAG, "getMACD(symbol=$symbol, timeframe=$timeframe) called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getMACD error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val bars = stockApiService.getBars(
                apiKey,
                apiSecret,
                symbol,
                timeframe = timeframe,
                limit = 1000
            )
            if (bars.size < 34) {
                val error = "Not enough data for MACD (found ${bars.size} bars at $timeframe)"
                Log.w(TAG, "getMACD warning: $error")
                return@runBlocking mapOf("status" to "error", "message" to error)
            }
            val closes = bars.map { it.close }
            val ema12 = calculateEMA(closes, 12)
            val ema26 = calculateEMA(closes, 26)

            val macdLine = ema12.zip(ema26).map { (e12, e26) -> e12 - e26 }
            // MACD line is valid only from index 25 onwards (because of EMA 26)
            val validMacdLine = macdLine.drop(25)
            val signalLine = calculateEMA(validMacdLine, 9)

            // Return the last 10 intervals of history
            val historyCount = 10
            val availableCount = signalLine.size
            val returnCount = minOf(historyCount, availableCount)

            val history = (0 until returnCount).map { i ->
                val signalIndex = availableCount - returnCount + i
                val barIndex = bars.size - returnCount + i
                mapOf(
                    "time" to bars[barIndex].timestamp,
                    "macd" to validMacdLine[signalIndex],
                    "signal" to signalLine[signalIndex],
                    "histogram" to (validMacdLine[signalIndex] - signalLine[signalIndex])
                )
            }

            val latestMacd = validMacdLine.last()
            val latestSignal = signalLine.last()
            val histogram = latestMacd - latestSignal

            val result = mapOf(
                "status" to "success",
                "symbol" to symbol,
                "timeframe" to timeframe,
                "latest" to mapOf(
                    "macd" to latestMacd,
                    "signal" to latestSignal,
                    "histogram" to histogram,
                    "advice" to if (histogram > 0) "bullish" else "bearish"
                ),
                "history" to history
            )
            Log.d(TAG, "getMACD result: success")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getMACD error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Place a buy or sell order for a stock.")
    fun placeOrder(
        @ToolParam(description = "The stock symbol, e.g., 'AAPL'.") symbol: String,
        @ToolParam(description = "The quantity of shares to buy or sell.") qty: String,
        @ToolParam(description = "The side of the order: 'buy' or 'sell'.") side: String,
        @ToolParam(description = "The order type, default is 'market'.") type: String = "market"
    ): Map<String, String> = runBlocking(coroutineContext) {
        Log.d(TAG, "placeOrder(symbol=$symbol, qty=$qty, side=$side, type=$type) called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "placeOrder error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val order = stockApiService.postOrder(apiKey, apiSecret, symbol, qty, side, type)
            val result = mapOf(
                "status" to "success",
                "order_id" to order.id,
                "symbol" to order.symbol,
                "side" to order.side,
                "qty" to (order.qty ?: "0"),
                "order_status" to order.status
            )
            Log.d(TAG, "placeOrder result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "placeOrder error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Cancel an open order by its ID.")
    fun cancelOrder(
        @ToolParam(description = "The ID of the order to cancel.") orderId: String
    ): Map<String, String> = runBlocking(coroutineContext) {
        Log.d(TAG, "cancelOrder(orderId=$orderId) called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "cancelOrder error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            stockApiService.deleteOrder(apiKey, apiSecret, orderId)
            val result = mapOf("status" to "success", "message" to "Order $orderId cancelled")
            Log.d(TAG, "cancelOrder result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "cancelOrder error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the latest news for specific stock symbols or general market news from the last 30 minutes.")
    fun getLatestNews(
        @ToolParam(description = "Comma-separated stock symbols, e.g., 'AAPL,TSLA'. If omitted, general market news is returned.") symbols: String? = null,
        @ToolParam(description = "The number of news items to return, default is 50.") limit: Int = 50
    ): Map<String, Any> = runBlocking(coroutineContext) {
        Log.d(TAG, "getLatestNews(symbols=$symbols, limit=$limit) called")
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            val error = "API credentials not set"
            Log.e(TAG, "getLatestNews error: $error")
            return@runBlocking mapOf("status" to "error", "message" to error)
        }
        try {
            val thirtyMinsAgo = Instant.now().minus(30, ChronoUnit.MINUTES)
            val startTime = DateTimeFormatter.ISO_INSTANT.format(thirtyMinsAgo)

            val news =
                stockApiService.getLatestNews(apiKey, apiSecret, symbols, limit, start = startTime)
            val result = mapOf(
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
            Log.d(TAG, "getLatestNews result: success (${news.size} items)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getLatestNews error: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Unknown error"))
        }
    }

    @Tool(description = "Get the current time in New York (EST/EDT), which is useful for stock market hours.")
    fun getCurrentNewYorkTime(): Map<String, String> {
        val nyTime = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val hour = nyTime.hour
        val minute = nyTime.minute
        val dayOfWeek = nyTime.dayOfWeek.value // 1 (Mon) to 7 (Sun)

        val isWeekday = dayOfWeek in 1..5
        val isMarketHours = isWeekday && (
                (hour == 9 && minute >= 30) ||
                        (hour in 10..15) ||
                        (hour == 16 && minute == 0)
                )

        return mapOf(
            "status" to "success",
            "time" to nyTime.format(formatter),
            "is_market_open_hours" to isMarketHours.toString(),
            "day_of_week" to nyTime.dayOfWeek.name,
            "note" to "Regular market hours are 9:30 AM to 4:00 PM ET on weekdays."
        )
    }

    /**
     * Calculates the Exponential Moving Average (EMA) for a list of data points.
     *
     * Unlike a Simple Moving Average (SMA), the EMA gives more weight to recent prices,
     * making it more sensitive to new market trends.
     *
     * The calculation follows these steps:
     * 1. Initial Seed: The first [period] data points are averaged (SMA) to start the EMA.
     * 2. Multiplier: A smoothing factor is calculated as `2 / (period + 1)`.
     * 3. Recursive Update: Each subsequent EMA is calculated as:
     *    `EMA = (Current Price - Previous EMA) * Multiplier + Previous EMA`
     *
     * In this project, this is used by [getMACD] to calculate the 12-period and 26-period
     * EMAs for the MACD line, and the 9-period EMA of that line for the Signal line.
     */
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
