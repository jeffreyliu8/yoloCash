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

package com.google.ai.edge.gallery.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM alpaca_credentials")
    fun getAllCredentials(): Flow<List<AlpacaCredentialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: AlpacaCredentialEntity)

    @Query("DELETE FROM alpaca_credentials WHERE name = :name")
    suspend fun deleteCredential(name: String)

    @Query("SELECT * FROM watchlist_stocks WHERE credentialName = :credentialName")
    fun getWatchlist(credentialName: String): Flow<List<WatchlistStockEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStock(stock: WatchlistStockEntity)

    @Query("DELETE FROM watchlist_stocks WHERE credentialName = :credentialName AND symbol = :symbol")
    suspend fun deleteStock(credentialName: String, symbol: String)
}
