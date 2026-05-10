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

package com.google.ai.edge.gallery.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlpacaAccount(
    @SerialName("id") val id: String,
    @SerialName("account_number") val accountNumber: String,
    @SerialName("status") val status: String,
    @SerialName("currency") val currency: String,
    @SerialName("buying_power") val buyingPower: String,
    @SerialName("regt_buying_power") val regtBuyingPower: String,
    @SerialName("daytrading_buying_power") val daytradingBuyingPower: String,
    @SerialName("cash") val cash: String,
    @SerialName("portfolio_value") val portfolioValue: String,
    @SerialName("equity") val equity: String,
    @SerialName("last_equity") val lastEquity: String,
    @SerialName("long_market_value") val longMarketValue: String,
    @SerialName("short_market_value") val shortMarketValue: String,
    @SerialName("initial_margin") val initialMargin: String,
    @SerialName("maintenance_margin") val maintenanceMargin: String,
    @SerialName("last_maintenance_margin") val lastMaintenanceMargin: String,
    @SerialName("sma") val sma: String,
    @SerialName("daytrade_count") val daytradeCount: Int,
)

@Serializable
data class AlpacaClock(
    @SerialName("timestamp") val timestamp: String,
    @SerialName("is_open") val isOpen: Boolean,
    @SerialName("next_open") val nextOpen: String,
    @SerialName("next_close") val nextClose: String,
)

@Serializable
data class AlpacaOrder(
    @SerialName("id") val id: String,
    @SerialName("symbol") val symbol: String,
    @SerialName("status") val status: String,
    @SerialName("qty") val qty: String?,
    @SerialName("side") val side: String,
    @SerialName("type") val type: String,
)

@Serializable
data class AlpacaTrade(
    @SerialName("p") val price: Double,
    @SerialName("s") val size: Int,
    @SerialName("t") val timestamp: String,
)

@Serializable
data class AlpacaLatestTrade(
    @SerialName("symbol") val symbol: String,
    @SerialName("trade") val trade: AlpacaTrade? = null,
)

@Serializable
data class AlpacaBar(
    @SerialName("t") val timestamp: String,
    @SerialName("o") val open: Double,
    @SerialName("h") val high: Double,
    @SerialName("l") val low: Double,
    @SerialName("c") val close: Double,
    @SerialName("v") val volume: Long,
)

@Serializable
data class AlpacaBarsResponse(
    @SerialName("bars") val bars: List<AlpacaBar>? = null,
    @SerialName("symbol") val symbol: String,
    @SerialName("next_page_token") val nextPageToken: String? = null,
)

@Serializable
data class AlpacaNews(
    @SerialName("id") val id: Long,
    @SerialName("headline") val headline: String,
    @SerialName("summary") val summary: String,
    @SerialName("author") val author: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("url") val url: String? = null,
    @SerialName("symbols") val symbols: List<String>,
    @SerialName("source") val source: String,
)

@Serializable
data class AlpacaNewsResponse(
    @SerialName("news") val news: List<AlpacaNews>? = null,
    @SerialName("next_page_token") val nextPageToken: String? = null,
)

@Serializable
data class AlpacaMover(
    @SerialName("symbol") val symbol: String,
    @SerialName("percent_change") val percentChange: Double,
    @SerialName("change") val change: Double,
    @SerialName("price") val price: Double,
)

@Serializable
data class AlpacaMoversResponse(
    @SerialName("gainers") val gainers: List<AlpacaMover> = emptyList(),
    @SerialName("losers") val losers: List<AlpacaMover> = emptyList(),
)

@Serializable
data class AlpacaMostActive(
    @SerialName("symbol") val symbol: String,
    @SerialName("volume") val volume: Long,
    @SerialName("trade_count") val tradeCount: Long,
)

@Serializable
data class AlpacaMostActiveResponse(
    @SerialName("most_actives") val mostActives: List<AlpacaMostActive> = emptyList(),
    @SerialName("last_updated") val lastUpdated: String? = null,
)

@Serializable
data class AlpacaWSMessage(
    @SerialName("T") val type: String,
    @SerialName("msg") val msg: String? = null,
    @SerialName("id") val id: Long? = null,
    @SerialName("headline") val headline: String? = null,
    @SerialName("summary") val summary: String? = null,
    @SerialName("author") val author: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("symbols") val symbols: List<String>? = null,
    @SerialName("source") val source: String? = null,
)

@Serializable
data class AlpacaPosition(
    @SerialName("asset_id") val assetId: String,
    @SerialName("symbol") val symbol: String,
    @SerialName("exchange") val exchange: String,
    @SerialName("asset_class") val assetClass: String,
    @SerialName("avg_entry_price") val avgEntryPrice: String,
    @SerialName("qty") val qty: String,
    @SerialName("side") val side: String,
    @SerialName("market_value") val marketValue: String,
    @SerialName("cost_basis") val costBasis: String,
    @SerialName("unrealized_pl") val unrealizedPl: String,
    @SerialName("unrealized_plpc") val unrealizedPlpc: String,
    @SerialName("unrealized_intraday_pl") val unrealizedIntradayPl: String,
    @SerialName("unrealized_intraday_plpc") val unrealizedIntradayPlpc: String,
    @SerialName("current_price") val currentPrice: String,
    @SerialName("lastday_price") val lastdayPrice: String,
    @SerialName("change_today") val changeToday: String,
)

@Serializable
data class PostOrderRequest(
    @SerialName("symbol") val symbol: String,
    @SerialName("qty") val qty: Int,
    @SerialName("notional") val notional: String? = null,
    @SerialName("side") val side: String,
    @SerialName("type") val type: String,
    @SerialName("time_in_force") val timeInForce: String,
    @SerialName("limit_price") val limitPrice: String? = null,
    @SerialName("stop_price") val stopPrice: String? = null,
    @SerialName("trail_price") val trailPrice: String? = null,
    @SerialName("trail_percent") val trailPercent: String? = null,
    @SerialName("extended_hours") val extendedHours: Boolean? = null,
    @SerialName("client_order_id") val clientOrderId: String? = null,
    @SerialName("order_class") val orderClass: String? = null,
)

@Serializable
data class AlpacaError(
    @SerialName("code") val code: Int? = null,
    @SerialName("message") val message: String? = null,
)

interface StockApiService {
    suspend fun getAccount(
        apiKey: String,
        apiSecret: String,
    ): AlpacaAccount

    suspend fun getClock(
        apiKey: String,
        apiSecret: String,
    ): AlpacaClock

    suspend fun getOrders(
        apiKey: String,
        apiSecret: String,
    ): List<AlpacaOrder>

    suspend fun getPositions(
        apiKey: String,
        apiSecret: String,
    ): List<AlpacaPosition>

    suspend fun getStockPrice(
        apiKey: String,
        apiSecret: String,
        symbol: String,
    ): Double

    suspend fun getBars(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        timeframe: String = "1Min",
        limit: Int = 1000,
        start: String? = null,
        end: String? = null
    ): List<AlpacaBar>

    suspend fun postOrder(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        qty: Int,
        side: String,
        type: String = "limit",
        timeInForce: String = "day",
        limitPrice: String? = null
    ): AlpacaOrder

    suspend fun deleteOrder(
        apiKey: String,
        apiSecret: String,
        orderId: String
    )

    suspend fun getLatestNews(
        apiKey: String,
        apiSecret: String,
        symbols: String? = null,
        limit: Int = 50,
        start: String? = null, //The inclusive start of the interval. Format: RFC-3339 or YYYY-MM-DD. Default: the beginning of the current day, but at least 15 minutes ago if the user doesn't have real-time access for the feed.
        end: String? = null //The inclusive end of the interval. Format: RFC-3339 or YYYY-MM-DD. Default: the current time if the user has a real-time access for the feed, otherwise 15 minutes before the current time.
    ): List<AlpacaNews>

    suspend fun getTopMovers(
        apiKey: String,
        apiSecret: String,
        top: Int = 10
    ): AlpacaMoversResponse

    suspend fun getMostActiveStocks(
        apiKey: String,
        apiSecret: String,
        by: String = "volume",
        top: Int = 10
    ): AlpacaMostActiveResponse
}
