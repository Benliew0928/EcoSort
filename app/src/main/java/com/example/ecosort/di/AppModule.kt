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

    @Provides
    @Singleton
    fun provideUserFollowDao(database: EcoSortDatabase): UserFollowDao {
        return database.userFollowDao()
    }

    @Provides
    @Singleton
    fun provideUserFriendDao(database: EcoSortDatabase): UserFriendDao {
        return database.userFriendDao()
    }

    @Provides
    @Singleton
    fun provideFriendRequestDao(database: EcoSortDatabase): FriendRequestDao {
        return database.friendRequestDao()
    }

    @Provides
    @Singleton
    fun provideFriendshipDao(database: EcoSortDatabase): FriendshipDao {
        return database.friendshipDao()
    }

    @Provides
    @Singleton
    fun provideBlockedUserDao(database: EcoSortDatabase): BlockedUserDao {
        return database.blockedUserDao()
    }

    @Provides
    @Singleton
    fun provideAdminDao(database: EcoSortDatabase): AdminDao {
        return database.adminDao()
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

    @Provides
    @Singleton
    fun provideFirebaseAuthService(): com.example.ecosort.data.firebase.FirebaseAuthService {
        return com.example.ecosort.data.firebase.FirebaseAuthService()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideSocialRepository(
        database: EcoSortDatabase,
        firestoreService: FirestoreService
    ): com.example.ecosort.data.repository.SocialRepository {
        return com.example.ecosort.data.repository.SocialRepository(database, firestoreService)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        database: EcoSortDatabase,
        preferencesManager: UserPreferencesManager,
        securityManager: SecurityManager,
        firestoreService: FirestoreService,
        firebaseAuthService: com.example.ecosort.data.firebase.FirebaseAuthService
    ): com.example.ecosort.data.repository.UserRepository {
        return com.example.ecosort.data.repository.UserRepository(
            userDao, database, preferencesManager, securityManager, firestoreService, firebaseAuthService
        )
    }

    @Provides
    @Singleton
    fun provideCommunityRepository(
        communityPostDao: CommunityPostDao,
        communityCommentDao: CommunityCommentDao,
        communityLikeDao: CommunityLikeDao,
        firestoreService: FirestoreService,
        firebaseStorageService: FirebaseStorageService,
        userPreferencesManager: UserPreferencesManager,
        userRepository: com.example.ecosort.data.repository.UserRepository
    ): com.example.ecosort.data.repository.CommunityRepository {
        return com.example.ecosort.data.repository.CommunityRepository(
            communityPostDao, communityCommentDao, communityLikeDao,
            firestoreService, firebaseStorageService, userPreferencesManager, userRepository
        )
    }

    @Provides
    @Singleton
    fun provideAdminRepository(
        database: EcoSortDatabase,
        firestoreService: FirestoreService,
        firebaseAuthService: com.example.ecosort.data.firebase.FirebaseAuthService
    ): com.example.ecosort.data.repository.AdminRepository {
        return com.example.ecosort.data.repository.AdminRepository(
            database, firestoreService, firebaseAuthService
        )
    }

    @Provides
    @Singleton
    fun provideProfileImageManager(
        profileImageStorageService: ProfileImageStorageService,
        userRepository: com.example.ecosort.data.repository.UserRepository,
        adminRepository: com.example.ecosort.data.repository.AdminRepository
    ): com.example.ecosort.utils.ProfileImageManager {
        return com.example.ecosort.utils.ProfileImageManager(
            profileImageStorageService, userRepository, adminRepository
        )
    }
    
    @Provides
    @Singleton
    fun provideRecycledItemRepository(
        database: com.example.ecosort.data.local.EcoSortDatabase,
        firestoreService: com.example.ecosort.data.firebase.FirestoreService
    ): com.example.ecosort.data.repository.RecycledItemRepository {
        return com.example.ecosort.data.repository.RecycledItemRepository(database, firestoreService)
    }

    @Provides
    @Singleton
    fun providePointsRepository(
        database: com.example.ecosort.data.local.EcoSortDatabase,
        firestoreService: com.example.ecosort.data.firebase.FirestoreService
    ): com.example.ecosort.data.repository.PointsRepository {
        return com.example.ecosort.data.repository.PointsRepository(database, firestoreService)
    }
}