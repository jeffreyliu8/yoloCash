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

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.contentType

class KtorStockApiService(
    private val client: HttpClient,
    private val baseUrl: String
) : StockApiService {
    override suspend fun getAccount(apiKey: String, apiSecret: String): AlpacaAccount {
        return client.get("${baseUrl}v2/account") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.body()
    }

    override suspend fun getClock(apiKey: String, apiSecret: String): AlpacaClock {
        return client.get("${baseUrl}v2/clock") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.body()
    }

    override suspend fun getOrders(apiKey: String, apiSecret: String): List<AlpacaOrder> {
        return client.get("${baseUrl}v2/orders") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.body()
    }

    override suspend fun getStockPrice(
        apiKey: String,
        apiSecret: String,
        symbol: String
    ): Double {
        val response: AlpacaLatestTrade = client.get("https://data.alpaca.markets/v2/stocks/$symbol/trades/latest") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.body()
        return response.trade.price
    }

    override suspend fun getBars(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        timeframe: String,
        limit: Int
    ): List<AlpacaBar> {
        val response: AlpacaBarsResponse = client.get("https://data.alpaca.markets/v2/stocks/$symbol/bars") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                parameters.append("timeframe", timeframe)
                parameters.append("limit", limit.toString())
            }
        }.body()
        return response.bars
    }

    override suspend fun postOrder(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        qty: String,
        side: String,
        type: String,
        timeInForce: String
    ): AlpacaOrder {
        return client.post("${baseUrl}v2/orders") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(
                mapOf(
                    "symbol" to symbol,
                    "qty" to qty,
                    "side" to side,
                    "type" to type,
                    "time_in_force" to timeInForce
                )
            )
        }.body()
    }

    override suspend fun deleteOrder(apiKey: String, apiSecret: String, orderId: String) {
        client.delete("${baseUrl}v2/orders/$orderId") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }
    }

    override suspend fun getLatestNews(
        apiKey: String,
        apiSecret: String,
        symbols: String?,
        limit: Int
    ): List<AlpacaNews> {
        val response: AlpacaNewsResponse = client.get("https://data.alpaca.markets/v1beta1/news") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                symbols?.let { parameters.append("symbols", it) }
                parameters.append("limit", limit.toString())
            }
        }.body()
        return response.news
    }
}
