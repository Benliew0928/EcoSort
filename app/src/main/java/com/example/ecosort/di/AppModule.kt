package com.example.ecosort.di

import android.content.Context
import com.example.ecosort.data.local.*
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.firebase.FirebaseStorageService
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.utils.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EcoSortDatabase {
        return EcoSortDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideUserDao(database: EcoSortDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideScannedItemDao(database: EcoSortDatabase): ScannedItemDao {
        return database.scannedItemDao()
    }

    @Provides
    @Singleton
    fun provideRecyclingStationDao(database: EcoSortDatabase): RecyclingStationDao {
        return database.recyclingStationDao()
    }

    @Provides
    @Singleton
    fun provideMarketplaceItemDao(database: EcoSortDatabase): MarketplaceItemDao {
        return database.marketplaceItemDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideUserPreferencesManager(@ApplicationContext context: Context): UserPreferencesManager {
        return UserPreferencesManager(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSecurityManager(): SecurityManager {
        return SecurityManager
    }
}

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseStorageService(): FirebaseStorageService {
        return FirebaseStorageService()
    }

    @Provides
    @Singleton
    fun provideFirestoreService(): FirestoreService {
        return FirestoreService()
    }
}