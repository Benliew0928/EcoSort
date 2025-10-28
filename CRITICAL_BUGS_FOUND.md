# Critical Bugs Found - Comprehensive App Check

## 🚨 **SEVERITY: CRITICAL - App-Breaking Bugs**

### **BUG #1: getUserById() Fails for Admins (36 locations!)**

**Problem:**
- Admins now use negative session IDs (e.g., `-1`)
- `userDao.getUserById(-1)` looks in `users` table with id=-1
- **Returns NULL** because admins are in `admins` table!
- **Affects 36 locations across the app!**

**Impact:**
- ❌ Admin cannot send chat messages (sender lookup fails)
- ❌ Admin cannot send friend requests (sender lookup fails)
- ❌ Admin cannot follow users (follower lookup fails)
- ❌ Admin profile operations fail
- ❌ Admin cannot update bio/location
- ❌ Admin cannot view their own data

**Affected Files:**
1. `ChatRepository.kt` - line 52: `userDao.getUserById(session.userId)` → NULL for admins
2. `FriendRepository.kt` - lines 49, 50, 130, 131, etc.: `userDao.getUserById(senderId/receiverId)`
3. `SocialRepository.kt` - lines 44, 45, etc.: `userDao.getUserById(followerId/followingId)`
4. `UserRepository.kt` - Multiple profile operations

**Example Failure:**
```kotlin
// Admin logs in with session.userId = -1
val sender = userDao.getUserById(-1)  // ❌ NULL!
val senderFirebaseUid = sender?.firebaseUid  // ❌ NULL!

// Firebase sync fails because senderFirebaseUid is null
if (!senderFirebaseUid.isNullOrEmpty()) {  // ❌ Skipped!
    firestoreService.sendChatMessage(...)  // Never executed
}
```

---

### **BUG #2: Channel ID Logic Broken for Negative IDs**

**Problem:**
In `ChatRepository.kt` line 291-295:
```kotlin
val channelId = if (user1Id < user2Id) {
    "chat_${user1Id}_${user2Id}"
} else {
    "chat_${user2Id}_${user1Id}"
}
```

**Issues:**
1. **Admin (-1) chats with User (2):**
   - `-1 < 2` = true
   - channelId = `"chat_-1_2"` ✅ Works

2. **Admin1 (-1) chats with Admin2 (-2):**
   - `-1 < -2` = **FALSE** (because -1 > -2)
   - Should be `"chat_-2_-1"` but creates `"chat_-1_-2"`
   - **Inconsistent channel ID!**

3. **User (1) chats with Admin (-1):**
   - `1 < -1` = **FALSE**
   - channelId = `"chat_-1_1"` ✅ Works

**Impact:**
- ❌ Admin-to-admin conversations may have inconsistent channel IDs
- ❌ Messages appear in wrong conversation
- ❌ Duplicate conversations created

---

### **BUG #3: Search Returns Admin with Positive ID**

**Problem:**
In `UserRepository.kt` line 359:
```kotlin
val adminUsers = matchingAdmins.map { admin ->
    User(
        id = admin.id,  // ❌ Uses positive admin ID (e.g., 1)
        firebaseUid = admin.firebaseUid,
        username = admin.username,
        userType = UserType.ADMIN
        // ...
    )
}
```

**Impact:**
- User searches and finds "Admin"
- Admin shows with `id=1` (positive)
- User clicks to chat/friend Admin
- System tries to look up user with `id=1`
- **Gets Ben instead of Admin!** (ID collision)

**Example:**
```
User "Liew" searches for "Admin"
Search returns: User(id=1, username="Admin", userType=ADMIN)

Liew clicks "Send Message" to Admin (id=1)
ChatRepository calls: getUserById(1)  // ❌ Gets Ben, not Admin!
Creates conversation with Ben instead of Admin!
```

---

### **BUG #4: Conversation Participant Lookup**

**Problem:**
When loading conversation list, system calls:
```kotlin
val user1 = userDao.getUserById(conversation.participant1Id)
val user2 = userDao.getUserById(conversation.participant2Id)
```

If participant is admin with negative ID, lookup fails!

---

## ✅ **SOLUTION: Unified User Lookup Helper**

### **Create Helper Function:**

```kotlin
// In UserRepository.kt
suspend fun getUserOrAdmin(userId: Long): User? {
    return if (userId < 0) {
        // Admin account - convert negative ID to positive
        val adminId = kotlin.math.abs(userId)
        val adminDao = database.adminDao()
        val admin = adminDao.getAdminById(adminId)
        
        admin?.let {
            User(
                id = userId,  // Keep negative ID for consistency
                firebaseUid = it.firebaseUid,
                username = it.username,
                email = it.email,
                passwordHash = it.passwordHash,
                userType = UserType.ADMIN,
                profileImageUrl = it.profileImageUrl,
                bio = it.bio,
                location = it.location,
                itemsRecycled = it.itemsRecycled,
                totalPoints = it.totalPoints,
                createdAt = it.createdAt,
                lastActive = it.lastLogin
            )
        }
    } else {
        // Regular user account
        userDao.getUserById(userId)
    }
}
```

### **Replace ALL Calls:**

Replace these 36 calls:
```kotlin
// OLD (BROKEN):
val user = userDao.getUserById(userId)

// NEW (FIXED):
val user = userRepository.getUserOrAdmin(userId)
```

---

### **Fix Channel ID Logic:**

```kotlin
// Use absolute values for comparison to ensure consistency
val channelId = if (kotlin.math.abs(user1Id) < kotlin.math.abs(user2Id)) {
    "chat_${user1Id}_${user2Id}"
} else {
    "chat_${user2Id}_${user1Id}"
}
```

**Explanation:**
- Compare absolute values: `abs(-1) < abs(2)` = `1 < 2` = true
- Ensures consistent ordering regardless of sign

---

### **Fix Search to Return Negative ID:**

```kotlin
val adminUsers = matchingAdmins.map { admin ->
    User(
        id = -admin.id,  // ✅ Use NEGATIVE ID for admin
        firebaseUid = admin.firebaseUid,
        username = admin.username,
        userType = UserType.ADMIN
        // ...
    )
}
```

---

## 📊 **Summary of Required Fixes:**

| Issue | Locations | Priority | Complexity |
|-------|-----------|----------|------------|
| getUserById() calls | 36 files | **CRITICAL** | High |
| Channel ID logic | 2 locations | **CRITICAL** | Low |
| Search returns positive ID | 1 location | **CRITICAL** | Low |

---

## ⚠️ **RECOMMENDATION:**

**Option 1: Fix All getUserById Calls (Comprehensive)**
- Create `getUserOrAdmin()` helper
- Replace all 36 calls
- Most robust solution

**Option 2: Revert Negative ID System (Simpler)**
- Go back to original approach
- Find different solution for admin/user separation
- Less code changes

**I recommend Option 1** - it's the proper fix that maintains the negative ID system while ensuring all lookups work correctly.

---

## 🧪 **Testing Required After Fix:**

1. Admin sends chat message ✅
2. Admin sends friend request ✅
3. Admin follows user ✅
4. Admin updates profile ✅
5. User searches for admin ✅
6. User chats with admin ✅
7. User adds admin as friend ✅
8. Admin-to-admin chat ✅

---

**Status: BLOCKED - App cannot function properly for admins until these bugs are fixed!**

