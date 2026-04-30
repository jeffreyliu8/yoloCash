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

interface StockApiService {
  suspend fun getAccount(
    apiKey: String,
    apiSecret: String,
  ): AlpacaAccount
}
