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

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header

data class AlpacaAccount(
  @SerializedName("id") val id: String,
  @SerializedName("account_number") val accountNumber: String,
  @SerializedName("status") val status: String,
  @SerializedName("currency") val currency: String,
  @SerializedName("buying_power") val buyingPower: String,
  @SerializedName("regt_buying_power") val regtBuyingPower: String,
  @SerializedName("daytrading_buying_power") val daytradingBuyingPower: String,
  @SerializedName("cash") val cash: String,
  @SerializedName("portfolio_value") val portfolioValue: String,
  @SerializedName("equity") val equity: String,
  @SerializedName("last_equity") val lastEquity: String,
  @SerializedName("long_market_value") val longMarketValue: String,
  @SerializedName("short_market_value") val shortMarketValue: String,
  @SerializedName("initial_margin") val initialMargin: String,
  @SerializedName("maintenance_margin") val maintenanceMargin: String,
  @SerializedName("last_maintenance_margin") val lastMaintenanceMargin: String,
  @SerializedName("sma") val sma: String,
  @SerializedName("daytrade_count") val daytradeCount: Int,
)

interface StockApiService {
  @GET("v2/account")
  suspend fun getAccount(
    @Header("APCA-API-KEY-ID") apiKey: String,
    @Header("APCA-API-SECRET-KEY") apiSecret: String,
  ): AlpacaAccount
}
