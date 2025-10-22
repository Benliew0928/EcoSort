package com.example.ecosort.di

import android.content.Context
import com.example.ecosort.data.local.*
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.firebase.FirebaseStorageService
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.ProfileImageStorageService
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
    fun provideChatMessageDao(database: EcoSortDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: EcoSortDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideCommunityPostDao(database: EcoSortDatabase): CommunityPostDao {
        return database.communityPostDao()
    }

    @Provides
    @Singleton
    fun provideCommunityCommentDao(database: EcoSortDatabase): CommunityCommentDao {
        return database.communityCommentDao()
    }

    @Provides
    @Singleton
    fun provideCommunityLikeDao(database: EcoSortDatabase): CommunityLikeDao {
        return database.communityLikeDao()
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

    @Provides
    @Singleton
    fun provideProfileImageStorageService(): ProfileImageStorageService {
        return ProfileImageStorageService()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSocialRepository(database: EcoSortDatabase): com.example.ecosort.data.repository.SocialRepository {
        return com.example.ecosort.data.repository.SocialRepository(database)
    }
}