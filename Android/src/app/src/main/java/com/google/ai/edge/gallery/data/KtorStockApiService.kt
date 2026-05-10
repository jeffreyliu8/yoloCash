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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class KtorStockApiService(
    private val client: HttpClient,
    private val baseUrl: String
) : StockApiService {

    private suspend inline fun <reified T> HttpResponse.handleResponse(): T {
        if (status.isSuccess()) {
            return body()
        } else {
            val errorBody = try {
                body<AlpacaError>()
            } catch (e: Exception) {
                null
            }
            throw Exception(errorBody?.message ?: "Alpaca API error: $status")
        }
    }

    override suspend fun getAccount(apiKey: String, apiSecret: String): AlpacaAccount {
        return client.get("${baseUrl}v2/account") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.handleResponse()
    }

    override suspend fun getClock(apiKey: String, apiSecret: String): AlpacaClock {
        return client.get("${baseUrl}v2/clock") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.handleResponse()
    }

    override suspend fun getOrders(apiKey: String, apiSecret: String): List<AlpacaOrder> {
        return client.get("${baseUrl}v2/orders") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.handleResponse()
    }

    override suspend fun getPositions(apiKey: String, apiSecret: String): List<AlpacaPosition> {
        return client.get("${baseUrl}v2/positions") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.handleResponse()
    }

    override suspend fun getStockPrice(
        apiKey: String,
        apiSecret: String,
        symbol: String
    ): Double {
        val response: AlpacaLatestTrade = client.get("https://data.alpaca.markets/v2/stocks/$symbol/trades/latest") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }.handleResponse()
        return response.trade?.price ?: 0.0
    }

    override suspend fun getBars(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        timeframe: String,
        limit: Int,
        start: String?,
        end: String?
    ): List<AlpacaBar> {
        val response: AlpacaBarsResponse = client.get("https://data.alpaca.markets/v2/stocks/$symbol/bars") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                parameters.append("timeframe", timeframe)
                parameters.append("limit", limit.toString())
                start?.let { parameters.append("start", it) }
                end?.let { parameters.append("end", it) }
            }
        }.handleResponse()
        return response.bars ?: emptyList()
    }

    override suspend fun postOrder(
        apiKey: String,
        apiSecret: String,
        symbol: String,
        qty: String,
        side: String,
        type: String,
        timeInForce: String,
        limitPrice: String?
    ): AlpacaOrder {
        return client.post("${baseUrl}v2/orders") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            contentType(io.ktor.http.ContentType.Application.Json)
            setBody(
                PostOrderRequest(
                    symbol = symbol,
                    qty = qty,
                    side = side,
                    type = type,
                    timeInForce = timeInForce,
                    limitPrice = limitPrice
                )
            )
        }.handleResponse()
    }

    override suspend fun deleteOrder(apiKey: String, apiSecret: String, orderId: String) {
        val response = client.delete("${baseUrl}v2/orders/$orderId") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
        }
        if (!response.status.isSuccess()) {
            val errorBody = try {
                response.body<AlpacaError>()
            } catch (e: Exception) {
                null
            }
            throw Exception(errorBody?.message ?: "Alpaca API error: ${response.status}")
        }
    }

    override suspend fun getLatestNews(
        apiKey: String,
        apiSecret: String,
        symbols: String?,
        limit: Int,
        start: String?,
        end: String?
    ): List<AlpacaNews> {
        val response: AlpacaNewsResponse = client.get("https://data.alpaca.markets/v1beta1/news") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                symbols?.let { parameters.append("symbols", it) }
                parameters.append("limit", limit.toString())
                start?.let { parameters.append("start", it) }
                end?.let { parameters.append("end", it) }
            }
        }.handleResponse()
        return response.news ?: emptyList()
    }

    override suspend fun getTopMovers(
        apiKey: String,
        apiSecret: String,
        top: Int
    ): AlpacaMoversResponse {
        return client.get("https://data.alpaca.markets/v1beta1/screener/stocks/movers") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                parameters.append("top", top.toString())
            }
        }.handleResponse()
    }

    override suspend fun getMostActiveStocks(
        apiKey: String,
        apiSecret: String,
        by: String,
        top: Int
    ): AlpacaMostActiveResponse {
        return client.get("https://data.alpaca.markets/v1beta1/screener/stocks/most-actives") {
            header("APCA-API-KEY-ID", apiKey)
            header("APCA-API-SECRET-KEY", apiSecret)
            url {
                parameters.append("by", by)
                parameters.append("top", top.toString())
            }
        }.handleResponse()
    }
}
