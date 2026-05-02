/*
 * Copyright 2025 Google LLC
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

package com.google.ai.edge.gallery.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.ai.edge.gallery.AppLifecycleProvider
import com.google.ai.edge.gallery.BenchmarkResultsSerializer
import com.google.ai.edge.gallery.CutoutsSerializer
import com.google.ai.edge.gallery.GalleryLifecycleProvider
import com.google.ai.edge.gallery.SettingsSerializer
import com.google.ai.edge.gallery.SkillsSerializer
import com.google.ai.edge.gallery.UserDataSerializer
import com.google.ai.edge.gallery.data.DataStoreRepository
import com.google.ai.edge.gallery.data.DefaultDataStoreRepository
import com.google.ai.edge.gallery.data.DefaultDownloadRepository
import com.google.ai.edge.gallery.data.DownloadRepository
import com.google.ai.edge.gallery.data.room.AppDatabase
import com.google.ai.edge.gallery.data.room.LogDao
import com.google.ai.edge.gallery.data.room.StockDao
import com.google.ai.edge.gallery.proto.BenchmarkResults
import com.google.ai.edge.gallery.proto.CutoutCollection
import com.google.ai.edge.gallery.proto.Settings
import com.google.ai.edge.gallery.proto.Skills
import com.google.ai.edge.gallery.proto.UserData
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger
import android.util.Log
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.google.ai.edge.gallery.data.StockApiService
import com.google.ai.edge.gallery.data.KtorStockApiService

@Module
@InstallIn(SingletonComponent::class)
internal object AppModule {

    // Alpaca paper trading base URL.
    private const val ALPACA_PAPER_BASE_URL = "https://paper-api.alpaca.markets/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.v("HttpClient", message)
                    }
                }
                level = LogLevel.HEADERS
            }
        }
    }

    // Provides the StockApiService
    @Provides
    @Singleton
    fun provideStockApiService(httpClient: HttpClient): StockApiService {
        return KtorStockApiService(httpClient, ALPACA_PAPER_BASE_URL)
    }

    // Provides the SettingsSerializer
    @Provides
    @Singleton
    fun provideSettingsSerializer(): Serializer<Settings> {
        return SettingsSerializer
    }

    // Provides the CutoutSerializer
    @Provides
    @Singleton
    fun provideCutoutSerializer(): Serializer<CutoutCollection> {
        return CutoutsSerializer
    }

    // Provides the UserDataSerializer
    @Provides
    @Singleton
    fun provideUserDataSerializer(): Serializer<UserData> {
        return UserDataSerializer
    }

    // Provides the BenchmarkResultsSerializer
    @Provides
    @Singleton
    fun provideBenchmarkResultsSerializer(): Serializer<BenchmarkResults> {
        return BenchmarkResultsSerializer
    }

    // Provides the SkillsSerializer
    @Provides
    @Singleton
    fun provideSkillsSerializer(): Serializer<Skills> {
        return SkillsSerializer
    }

    // Provides the AppDatabase
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    db.execSQL("INSERT INTO alpaca_credentials (name, apiKey, apiSecret) VALUES ('Account 1', 'PKMN74Y7PMLTGKIGBKL4VV7DUF', 'CCyTsMJ8W4kQi1zysW57Ga1CUen4Q27mNDD9MRR1cu1C')")
                    db.execSQL("INSERT INTO alpaca_credentials (name, apiKey, apiSecret) VALUES ('Account 2', 'PK3SIFRQMOJLSE443UBGHOZSCY', 'DF5QYtvT4dTDM5Mva3yFrYf2c4TYhhaSkifaTBkEsTo4')")
                    db.execSQL("INSERT INTO alpaca_credentials (name, apiKey, apiSecret) VALUES ('Account 3', 'PKGETVNBNXWAY44G7JOHKT3BYV', 'ETw7JGPuLP5f8eLbFUXnJutiN7d5qcEEZNEozWWaVK5u')")
                }
            })
            .fallbackToDestructiveMigration(true)
            .build()
    }

    // Provides the StockDao
    @Provides
    @Singleton
    fun provideStockDao(database: AppDatabase): StockDao {
        return database.stockDao()
    }

    // Provides the LogDao
    @Provides
    @Singleton
    fun provideLogDao(database: AppDatabase): LogDao {
        return database.logDao()
    }

    // Provides DataStore<Settings>
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        settingsSerializer: Serializer<Settings>,
    ): DataStore<Settings> {
        return DataStoreFactory.create(
            serializer = settingsSerializer,
            produceFile = { context.dataStoreFile("settings.pb") },
        )
    }

    // Provides DataStore<CutoutCollection>
    @Provides
    @Singleton
    fun provideCutoutsDataStore(
        @ApplicationContext context: Context,
        cutoutsSerializer: Serializer<CutoutCollection>,
    ): DataStore<CutoutCollection> {
        return DataStoreFactory.create(
            serializer = cutoutsSerializer,
            produceFile = { context.dataStoreFile("cutouts.pb") },
        )
    }

    // Provides DataStore<UserData>
    @Provides
    @Singleton
    fun provideUserDataDataStore(
        @ApplicationContext context: Context,
        userDataSerializer: Serializer<UserData>,
    ): DataStore<UserData> {
        return DataStoreFactory.create(
            serializer = userDataSerializer,
            produceFile = { context.dataStoreFile("user_data.pb") },
        )
    }

    // Provides DataStore<BenchmarkResults>
    @Provides
    @Singleton
    fun provideBenchmarkResultsDataStore(
        @ApplicationContext context: Context,
        benchmarkResultsSerializer: Serializer<BenchmarkResults>,
    ): DataStore<BenchmarkResults> {
        return DataStoreFactory.create(
            serializer = benchmarkResultsSerializer,
            produceFile = { context.dataStoreFile("benchmark_results.pb") },
        )
    }

    // Provides DataStore<Skills>
    @Provides
    @Singleton
    fun provideSkillsDataStore(
        @ApplicationContext context: Context,
        skillsSerializer: Serializer<Skills>,
    ): DataStore<Skills> {
        return DataStoreFactory.create(
            serializer = skillsSerializer,
            produceFile = { context.dataStoreFile("skills.pb") },
        )
    }

    // Provides AppLifecycleProvider
    @Provides
    @Singleton
    fun provideAppLifecycleProvider(): AppLifecycleProvider {
        return GalleryLifecycleProvider()
    }

    // Provides DataStoreRepository
    @Provides
    @Singleton
    fun provideDataStoreRepository(
        dataStore: DataStore<Settings>,
        userDataDataStore: DataStore<UserData>,
        cutoutsDataStore: DataStore<CutoutCollection>,
        benchmarkResultsStore: DataStore<BenchmarkResults>,
        skillsDataStore: DataStore<Skills>,
    ): DataStoreRepository {
        return DefaultDataStoreRepository(
            dataStore,
            userDataDataStore,
            cutoutsDataStore,
            benchmarkResultsStore,
            skillsDataStore,
        )
    }

    // Provides DownloadRepository
    @Provides
    @Singleton
    fun provideDownloadRepository(
        @ApplicationContext context: Context,
        lifecycleProvider: AppLifecycleProvider,
    ): DownloadRepository {
        return DefaultDownloadRepository(context, lifecycleProvider)
    }
}
