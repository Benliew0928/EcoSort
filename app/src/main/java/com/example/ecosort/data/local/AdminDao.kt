package com.example.ecosort.data.local

import androidx.room.*
import com.example.ecosort.data.model.Admin
import com.example.ecosort.data.model.AdminAction
import kotlinx.coroutines.flow.Flow

@Dao
interface AdminDao {
    
    // ==================== ADMIN CRUD OPERATIONS ====================
    
    @Query("SELECT * FROM admins WHERE username = :username AND isActive = 1")
    suspend fun getAdminByUsername(username: String): Admin?
    
    @Query("SELECT * FROM admins WHERE email = :email AND isActive = 1")
    suspend fun getAdminByEmail(email: String): Admin?
    
    @Query("SELECT * FROM admins WHERE id = :adminId")
    suspend fun getAdminById(adminId: Long): Admin?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: Admin): Long
    
    @Update
    suspend fun updateAdmin(admin: Admin)

    @Delete
    suspend fun deleteAdmin(admin: Admin)
    
    @Query("UPDATE admins SET lastLogin = :timestamp WHERE id = :adminId")
    suspend fun updateLastLogin(adminId: Long, timestamp: Long)
    
    @Query("UPDATE admins SET isActive = :isActive WHERE id = :adminId")
    suspend fun updateAdminStatus(adminId: Long, isActive: Boolean)
    
    @Query("UPDATE admins SET profileImageUrl = :imageUrl WHERE id = :adminId")
    suspend fun updateProfileImage(adminId: Long, imageUrl: String?)
    
    @Query("UPDATE admins SET bio = :bio WHERE id = :adminId")
    suspend fun updateBio(adminId: Long, bio: String?)
    
    @Query("UPDATE admins SET location = :location WHERE id = :adminId")
    suspend fun updateLocation(adminId: Long, location: String?)
    
    @Query("SELECT * FROM admins WHERE isActive = 1")
    suspend fun getAllActiveAdmins(): List<Admin>

    @Query("SELECT * FROM admins")
    suspend fun getAllAdmins(): List<Admin>
    
    // ==================== ADMIN ACTION LOGGING ====================
    
    @Insert
    suspend fun logAdminAction(action: AdminAction)
    
    @Query("SELECT * FROM admin_actions WHERE adminId = :adminId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAdminActions(adminId: Long, limit: Int = 50): List<AdminAction>
    
    @Query("SELECT * FROM admin_actions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllAdminActions(limit: Int = 100): List<AdminAction>
    
    @Query("SELECT * FROM admin_actions WHERE targetUserId = :userId ORDER BY timestamp DESC")
    suspend fun getUserActionHistory(userId: Long): List<AdminAction>
}
