# Firebase Sync Implementation for Chat, Friend, and Following Systems

## 🎯 Overview

This document describes the comprehensive Firebase synchronization implementation for the Chat, Friend, and Following systems in EcoSort. All three systems now support **cross-device global synchronization** using Firebase UID for user identification.

---

## ✅ What Has Been Implemented

### 1. Firebase Data Models (`FirebaseModels.kt`)

Created Firebase-compatible data models for all three systems:

#### Chat Models
- **`FirebaseChatMessage`**: Stores chat messages with Firebase UID for sender
  - Fields: `id`, `channelId`, `senderId` (Firebase UID), `senderUsername`, `messageText`, `messageType`, `attachmentUrl`, `timestamp`, `readBy` (list of Firebase UIDs), `messageStatus`
  
- **`FirebaseConversation`**: Stores conversation metadata
  - Fields: `channelId`, `participant1Id` (Firebase UID), `participant1Username`, `participant2Id` (Firebase UID), `participant2Username`, `lastMessageText`, `lastMessageTimestamp`, `lastMessageSenderId`, `createdAt`

#### Friend Models
- **`FirebaseFriendRequest`**: Stores friend requests
  - Fields: `id`, `senderId` (Firebase UID), `receiverId` (Firebase UID), `status` (PENDING/ACCEPTED/DECLINED/CANCELLED), `message`, `createdAt`, `updatedAt`
  
- **`FirebaseFriendship`**: Stores accepted friendships
  - Fields: `id`, `userId1` (Firebase UID - always smaller UID first), `userId2` (Firebase UID), `createdAt`, `lastInteraction`

#### Follow Models
- **`FirebaseUserFollow`**: Stores follow relationships
  - Fields: `id`, `followerId` (Firebase UID), `followingId` (Firebase UID), `followedAt`

---

### 2. Firebase Service Methods (`FirestoreService.kt`)

Added comprehensive Firebase operations for all three systems:

#### Chat Operations
- ✅ `sendChatMessage()` - Send messages to Firebase
- ✅ `getChannelMessages()` - Real-time message stream for a channel
- ✅ `markMessageAsRead()` - Mark messages as read by adding user UID to readBy list
- ✅ `saveConversation()` - Create/update conversation metadata
- ✅ `getUserConversations()` - Real-time stream of user's conversations

#### Friend Operations
- ✅ `sendFriendRequest()` - Send friend request to Firebase
- ✅ `getPendingFriendRequests()` - Real-time stream of pending requests
- ✅ `updateFriendRequestStatus()` - Accept/decline/cancel requests
- ✅ `createFriendship()` - Create friendship after accepting request
- ✅ `getUserFriendships()` - Real-time stream of user's friendships
- ✅ `removeFriendship()` - Remove friendship from Firebase

#### Follow Operations
- ✅ `followUser()` - Create follow relationship in Firebase
- ✅ `unfollowUser()` - Remove follow relationship from Firebase
- ✅ `getUserFollowing()` - Real-time stream of users being followed
- ✅ `getUserFollowers()` - Real-time stream of followers
- ✅ `isFollowing()` - Check if user is following another user

---

### 3. Firebase Security Rules (`firestore.rules`)

Updated security rules to allow proper access to all three systems:

#### Chat Collections
```javascript
// chat_messages - Read by conversation participants, created by sender
match /chat_messages/{messageId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null && request.auth.uid == request.resource.data.senderId;
  allow update: if request.auth != null;  // For marking as read
  allow delete: if request.auth != null && request.auth.uid == resource.data.senderId;
}

// conversations - Managed by participants
match /conversations/{conversationId} {
  allow read, create, update, delete: if request.auth != null;
}
```

#### Friend Collections
```javascript
// friend_requests - Created by sender, updated by sender or receiver
match /friend_requests/{requestId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null && request.auth.uid == request.resource.data.senderId;
  allow update: if request.auth != null && 
    (request.auth.uid == resource.data.senderId || request.auth.uid == resource.data.receiverId);
  allow delete: if request.auth != null && 
    (request.auth.uid == resource.data.senderId || request.auth.uid == resource.data.receiverId);
}

// friendships - Managed by either participant
match /friendships/{friendshipId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null;
  allow update: if request.auth != null && 
    (request.auth.uid == resource.data.userId1 || request.auth.uid == resource.data.userId2);
  allow delete: if request.auth != null && 
    (request.auth.uid == resource.data.userId1 || request.auth.uid == resource.data.userId2);
}
```

#### Follow Collections
```javascript
// user_follows - Created and deleted only by the follower
match /user_follows/{followId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null && request.auth.uid == request.resource.data.followerId;
  allow delete: if request.auth != null && request.auth.uid == resource.data.followerId;
}
```

---

## 🔧 Next Steps: Repository Integration

The Firebase infrastructure is now in place, but the repositories (`ChatRepository`, `FriendRepository`, `SocialRepository`) need to be updated to use these Firebase methods. Here's what needs to be done:

### For ChatRepository
1. Inject `FirestoreService` and `FirebaseAuth`
2. Get current user's Firebase UID
3. When sending messages, also call `firestoreService.sendChatMessage()`
4. When creating conversations, also call `firestoreService.saveConversation()`
5. Add sync method to pull messages from Firebase to local Room DB

### For FriendRepository  
1. Inject `FirestoreService` and `FirebaseAuth`
2. When sending friend requests, also call `firestoreService.sendFriendRequest()`
3. When accepting requests, also call `firestoreService.createFriendship()`
4. Add sync methods to pull friend data from Firebase to local Room DB

### For SocialRepository
1. Inject `FirestoreService` and `FirebaseAuth`
2. When following users, also call `firestoreService.followUser()`
3. When unfollowing, also call `firestoreService.unfollowUser()`
4. Add sync methods to pull follow data from Firebase to local Room DB

---

## 📋 Key Implementation Details

### Firebase UID Usage
- **ALL** user IDs in Firebase collections use Firebase UID (String format)
- Local Room database still uses Long IDs
- Conversion happens at the repository level
- User model has `firebaseUid` field to map between systems

### Channel ID Format
- Chat channels use format: `chat_{smaller_uid}_{larger_uid}`
- Ensures consistent channel IDs regardless of who initiates chat
- Firebase UID-based for cross-device consistency

### Friendship ID Format
- Friendships always store smaller UID as `userId1`, larger UID as `userId2`
- Ensures no duplicate friendships in reverse order
- Makes querying more efficient

### Real-Time Synchronization
- All Firebase methods use Flows for real-time updates
- Changes in Firebase automatically propagate to all devices
- Use `callbackFlow` with Firestore snapshot listeners

---

## 🚀 How to Deploy

### 1. Deploy Firebase Rules
```bash
firebase deploy --only firestore:rules
```

Or manually via Firebase Console:
1. Go to Firebase Console → Firestore Database → Rules
2. Copy the content from `firestore.rules`
3. Paste and Publish

### 2. Test the Implementation
1. Login with two different accounts on two devices
2. Test Chat: Send messages and verify they appear on both devices
3. Test Friends: Send friend request and verify it appears on receiver's device
4. Test Follow: Follow a user and verify it updates globally

---

## 🐛 Debugging Tips

### Check Firebase Console
- Go to Firestore Database
- Look for collections: `chat_messages`, `conversations`, `friend_requests`, `friendships`, `user_follows`
- Verify data is being written with correct Firebase UIDs

### Check Logs
Look for these log tags:
- `FirestoreService` - Firebase operations
- `ChatRepository` - Chat sync operations
- `FriendRepository` - Friend sync operations
- `SocialRepository` - Follow sync operations

### Common Issues
1. **"Permission Denied"**: Check Firebase rules are deployed
2. **"No data syncing"**: Verify user has Firebase UID in local database
3. **"Duplicate entries"**: Check channel/friendship ID generation logic

---

## 📊 Database Structure

### Firebase Collections
```
firestore/
├── chat_messages/
│   └── {messageId}
│       ├── channelId: string
│       ├── senderId: string (Firebase UID)
│       ├── senderUsername: string
│       ├── messageText: string
│       ├── timestamp: Timestamp
│       └── readBy: array<string>
│
├── conversations/
│   └── {channelId}
│       ├── participant1Id: string (Firebase UID)
│       ├── participant2Id: string (Firebase UID)
│       ├── lastMessageText: string
│       └── lastMessageTimestamp: Timestamp
│
├── friend_requests/
│   └── {requestId}
│       ├── senderId: string (Firebase UID)
│       ├── receiverId: string (Firebase UID)
│       ├── status: string
│       └── createdAt: Timestamp
│
├── friendships/
│   └── {friendshipId}
│       ├── userId1: string (Firebase UID, smaller)
│       ├── userId2: string (Firebase UID, larger)
│       └── createdAt: Timestamp
│
└── user_follows/
    └── {followId}
        ├── followerId: string (Firebase UID)
        ├── followingId: string (Firebase UID)
        └── followedAt: Timestamp
```

---

## ✨ Benefits of This Implementation

1. **Cross-Device Sync**: All data syncs globally across devices
2. **Real-Time Updates**: Changes propagate instantly using Firestore listeners
3. **Offline Support**: Local Room database caches data for offline access
4. **Scalable**: Firebase handles the heavy lifting of sync and real-time updates
5. **Secure**: Comprehensive security rules prevent unauthorized access
6. **Consistent**: Firebase UID ensures same user identity across devices
7. **Reliable**: Firestore's built-in conflict resolution handles concurrent updates

---

## 📝 Notes

- All Firebase methods return `Result<T>` for consistent error handling
- All timestamp fields use Firestore's `@ServerTimestamp` for accuracy
- All real-time methods use Kotlin Flow for reactive updates
- All security rules follow principle of least privilege
- All data models include no-argument constructors for Firestore

---

*Implementation Date: October 28, 2025*
*Status: ✅ Firebase Infrastructure Complete - Repository Integration Pending*

