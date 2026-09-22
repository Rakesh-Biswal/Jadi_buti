package com.chefotech.jadibuti.di

import android.content.Context
import androidx.room.Room
import com.chefotech.jadibuti.data.local.AppDatabase
import com.chefotech.jadibuti.data.remote.ApiClient
import com.chefotech.jadibuti.data.remote.ApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "jadibuti.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun members(db: AppDatabase) = db.members()
    @Provides fun medicines(db: AppDatabase) = db.medicines()
    @Provides fun events(db: AppDatabase) = db.events()
    @Provides fun transactions(db: AppDatabase) = db.transactions()
    @Provides fun outbox(db: AppDatabase) = db.outbox()
    @Provides fun syncState(db: AppDatabase) = db.syncState()

    @Provides
    @Singleton
    fun api(client: ApiClient): ApiService = client.api
}
