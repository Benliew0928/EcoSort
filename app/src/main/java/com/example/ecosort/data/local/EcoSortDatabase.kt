package com.example.ecosort.data.local

import android.content.Context
import androidx.room.*
import com.example.ecosort.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

// ==================== TYPE CONVERTERS ====================
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromWasteCategoryList(value: List<WasteCategory>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toWasteCategoryList(value: String): List<WasteCategory> {
        val type = object : TypeToken<List<WasteCategory>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromUserType(value: UserType): String = value.name

    @TypeConverter
    fun toUserType(value: String): UserType = UserType.valueOf(value)

    @TypeConverter
    fun fromWasteCategory(value: WasteCategory): String = value.name

    @TypeConverter
    fun toWasteCategory(value: String): WasteCategory = WasteCategory.valueOf(value)

    @TypeConverter
    fun fromItemCondition(value: ItemCondition): String = value.name

    @TypeConverter
    fun toItemCondition(value: String): ItemCondition = ItemCondition.valueOf(value)

    @TypeConverter
    fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter
    fun fromItemAction(value: ItemAction?): String? = value?.name

    @TypeConverter
    fun toItemAction(value: String?): ItemAction? = value?.let { ItemAction.valueOf(it) }
}

// ==================== USER DAO ====================
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET itemsRecycled = itemsRecycled + 1 WHERE id = :userId")
    suspend fun incrementItemsRecycled(userId: Long)

    @Query("UPDATE users SET totalEarnings = totalEarnings + :amount WHERE id = :userId")
    suspend fun addEarnings(userId: Long, amount: Double)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)
}

// ==================== SCANNED ITEMS DAO ====================
@Dao
interface ScannedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedItem(item: ScannedItem): Long

    @Query("SELECT * FROM scanned_items WHERE userId = :userId ORDER BY scannedAt DESC")
    fun getUserScannedItems(userId: Long): Flow<List<ScannedItem>>

    @Query("SELECT * FROM scanned_items WHERE userId = :userId ORDER BY scannedAt DESC LIMIT 1")
    suspend fun getLastScannedItem(userId: Long): ScannedItem?

    @Query("SELECT * FROM scanned_items WHERE id = :itemId LIMIT 1")
    suspend fun getScannedItemById(itemId: Long): ScannedItem?

    @Update
    suspend fun updateScannedItem(item: ScannedItem)

    @Query("UPDATE scanned_items SET action = :action WHERE id = :itemId")
    suspend fun updateItemAction(itemId: Long, action: ItemAction)

    @Query("SELECT COUNT(*) FROM scanned_items WHERE userId = :userId AND isRecyclable = 1")
    suspend fun getRecyclableCount(userId: Long): Int

    @Query("DELETE FROM scanned_items WHERE id = :itemId")
    suspend fun deleteScannedItem(itemId: Long)
}

// ==================== RECYCLING STATION DAO ====================
@Dao
interface RecyclingStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RecyclingStation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RecyclingStation>)

    @Query("SELECT * FROM recycling_stations")
    fun getAllStations(): Flow<List<RecyclingStation>>

    @Query("SELECT * FROM recycling_stations WHERE id = :stationId LIMIT 1")
    suspend fun getStationById(stationId: Long): RecyclingStation?

    @Update
    suspend fun updateStation(station: RecyclingStation)

    @Query("DELETE FROM recycling_stations WHERE id = :stationId")
    suspend fun deleteStation(stationId: Long)

    @Query("DELETE FROM recycling_stations")
    suspend fun deleteAllStations()
}

// ==================== MARKETPLACE ITEM DAO ====================
@Dao
interface MarketplaceItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MarketplaceItem): Long

    @Query("SELECT * FROM marketplace_items WHERE status = 'AVAILABLE' ORDER BY postedAt DESC")
    fun getAvailableItems(): Flow<List<MarketplaceItem>>

    @Query("SELECT * FROM marketplace_items WHERE sellerId = :userId ORDER BY postedAt DESC")
    fun getUserItems(userId: Long): Flow<List<MarketplaceItem>>

    @Query("SELECT * FROM marketplace_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): MarketplaceItem?

    @Query("SELECT * FROM marketplace_items WHERE title = :title LIMIT 1")
    suspend fun getItemByTitle(title: String): MarketplaceItem?

    @Query("DELETE FROM marketplace_items WHERE title IN (:titles)")
    suspend fun deleteItemsByTitles(titles: List<String>)

    @Query("SELECT * FROM marketplace_items WHERE category = :category AND status = 'AVAILABLE' ORDER BY postedAt DESC")
    fun getItemsByCategory(category: WasteCategory): Flow<List<MarketplaceItem>>

    @Update
    suspend fun updateItem(item: MarketplaceItem)

    @Query("UPDATE marketplace_items SET status = :status WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Long, status: ItemStatus)

    @Query("UPDATE marketplace_items SET views = views + 1 WHERE id = :itemId")
    suspend fun incrementViews(itemId: Long)

    @Query("DELETE FROM marketplace_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}

// ==================== MAIN DATABASE ====================
@Database(
    entities = [
        User::class,
        ScannedItem::class,
        RecyclingStation::class,
        MarketplaceItem::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EcoSortDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun scannedItemDao(): ScannedItemDao
    abstract fun recyclingStationDao(): RecyclingStationDao
    abstract fun marketplaceItemDao(): MarketplaceItemDao

    companion object {
        @Volatile
        private var INSTANCE: EcoSortDatabase? = null

        fun getDatabase(context: Context): EcoSortDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EcoSortDatabase::class.java,
                    "ecosort_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}