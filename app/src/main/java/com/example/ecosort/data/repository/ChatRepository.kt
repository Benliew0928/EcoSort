package com.example.ecosort.data.repository

import com.example.ecosort.data.local.ChatMessageDao
import com.example.ecosort.data.local.ConversationDao
import com.example.ecosort.data.model.ChatMessage
import com.example.ecosort.data.model.Conversation
import com.example.ecosort.data.model.MessageType
import com.example.ecosort.data.model.MessageStatus
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val conversationDao: ConversationDao,
    private val preferencesManager: UserPreferencesManager,
    private val userRepository: UserRepository
) {

    /**
     * Get messages for a specific channel
     */
    fun getMessagesForChannel(channelId: String): Flow<List<ChatMessage>> {
        return chatMessageDao.getMessagesForChannel(channelId)
    }

    /**
     * Send a text message
     */
    suspend fun sendTextMessage(
        channelId: String,
        messageText: String
    ): Result<ChatMessage> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val message = ChatMessage(
                channelId = channelId,
                senderId = session.userId,
                senderUsername = session.username,
                messageText = messageText,
                messageType = MessageType.TEXT
            )

            val messageId = chatMessageDao.insertMessage(message)
            val createdMessage = message.copy(id = messageId)

            // Update message status to SENT after successful insertion
            chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENT)
            android.util.Log.d("ChatRepository", "Text message status updated to SENT for message ID: $messageId")

            // Update conversation with new message
            updateConversationWithMessage(channelId, messageText, session.userId)

            Result.Success(createdMessage)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Send an image message
     */
    suspend fun sendImageMessage(
        channelId: String,
        imageUrl: String
    ): Result<ChatMessage> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val message = ChatMessage(
                channelId = channelId,
                senderId = session.userId,
                senderUsername = session.username,
                messageText = "📷 Image",
                messageType = MessageType.IMAGE,
                attachmentUrl = imageUrl,
                attachmentType = "image"
            )

            val messageId = chatMessageDao.insertMessage(message)
            val createdMessage = message.copy(id = messageId)

            // Update message status to SENT after successful insertion
            chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENT)

            // Update conversation with new message
            updateConversationWithMessage(channelId, "📷 Image", session.userId)

            Result.Success(createdMessage)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Send a voice message
     */
    suspend fun sendVoiceMessage(
        channelId: String,
        voiceFileUrl: String,
        duration: Long
    ): Result<ChatMessage> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val message = ChatMessage(
                channelId = channelId,
                senderId = session.userId,
                senderUsername = session.username,
                messageText = "🎤 Voice message",
                messageType = MessageType.VOICE,
                attachmentUrl = voiceFileUrl,
                attachmentType = "voice",
                attachmentDuration = duration
            )

            val messageId = chatMessageDao.insertMessage(message)
            val createdMessage = message.copy(id = messageId)

            // Update message status to SENT after successful insertion
            chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENT)

            // Update conversation with new message
            updateConversationWithMessage(channelId, "🎤 Voice message", session.userId)

            Result.Success(createdMessage)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Send a file message
     */
    suspend fun sendFileMessage(
        channelId: String,
        fileUrl: String,
        fileName: String
    ): Result<ChatMessage> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val message = ChatMessage(
                channelId = channelId,
                senderId = session.userId,
                senderUsername = session.username,
                messageText = "📎 $fileName",
                messageType = MessageType.FILE,
                attachmentUrl = fileUrl,
                attachmentType = "file"
            )

            val messageId = chatMessageDao.insertMessage(message)
            val createdMessage = message.copy(id = messageId)

            // Update message status to SENT after successful insertion
            chatMessageDao.updateMessageStatus(messageId, MessageStatus.SENT)

            // Update conversation with new message
            updateConversationWithMessage(channelId, "📎 $fileName", session.userId)

            Result.Success(createdMessage)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Mark messages as read
     */
    suspend fun markMessagesAsRead(channelId: String): Result<Unit> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            // Mark messages as read for the current user
            chatMessageDao.markMessagesAsRead(channelId, session.userId)
            
            // Update message status to SEEN for messages from other users
            chatMessageDao.updateMessageStatusToSeen(channelId, session.userId)
            android.util.Log.d("ChatRepository", "Messages marked as SEEN for channel: $channelId by user: ${session.userId}")
            
            // Unread count is now calculated dynamically by the database query
            android.util.Log.d("ChatRepository", "Messages marked as read for user: ${session.userId} in channel: $channelId")
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get unread message count for a channel
     */
    suspend fun getUnreadCount(channelId: String): Result<Int> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            val count = chatMessageDao.getUnreadCount(channelId, session.userId)
            Result.Success(count)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get the last message for a channel
     */
    suspend fun getLastMessage(channelId: String): Result<ChatMessage?> {
        return try {
            val message = chatMessageDao.getLastMessageForChannel(channelId)
            Result.Success(message)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get conversations for the current user
     */
    fun getConversationsForUser(userId: Long): Flow<List<Conversation>> {
        return conversationDao.getConversationsForUser(userId)
    }

    /**
     * Create or get a conversation between two users
     */
    suspend fun createOrGetConversation(user1Id: Long, user1Username: String, user2Id: Long, user2Username: String): Result<Conversation> {
        return try {
            // Create a consistent channel ID (always smaller ID first)
            // Use "chat_" prefix for compatibility with old conversations
            val channelId = if (user1Id < user2Id) {
                "chat_${user1Id}_${user2Id}"
            } else {
                "chat_${user2Id}_${user1Id}"
            }

            // Check if conversation already exists
            val existingConversation = conversationDao.getConversationByChannelId(channelId)
            if (existingConversation != null) {
                return Result.Success(existingConversation)
            }

            // Create new conversation
            val conversation = Conversation(
                channelId = channelId,
                participant1Id = if (user1Id < user2Id) user1Id else user2Id,
                participant1Username = if (user1Id < user2Id) user1Username else user2Username,
                participant2Id = if (user1Id < user2Id) user2Id else user1Id,
                participant2Username = if (user1Id < user2Id) user2Username else user1Username
            )

            conversationDao.insertConversation(conversation)
            Result.Success(conversation)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get or create a conversation between two users (simplified version with just user IDs)
     */
    suspend fun getOrCreateConversation(user1Id: Long, user2Id: Long): Result<Conversation> {
        return try {
            // Create a consistent channel ID (always smaller ID first)
            // Use "chat_" prefix for compatibility with old conversations
            val channelId = if (user1Id < user2Id) {
                "chat_${user1Id}_${user2Id}"
            } else {
                "chat_${user2Id}_${user1Id}"
            }

            android.util.Log.d("ChatRepository", "Looking for conversation with channelId: $channelId")

            // Check if conversation already exists
            var existingConversation = conversationDao.getConversationByChannelId(channelId)
            if (existingConversation != null) {
                android.util.Log.d("ChatRepository", "Found existing conversation: $channelId")
                return Result.Success(existingConversation)
            }

            // Check for old format conversations (user_ prefix) and migrate them
            val oldChannelId = if (user1Id < user2Id) {
                "user_${user1Id}_${user2Id}"
            } else {
                "user_${user2Id}_${user1Id}"
            }
            
            existingConversation = conversationDao.getConversationByChannelId(oldChannelId)
            if (existingConversation != null) {
                android.util.Log.d("ChatRepository", "Found old format conversation, migrating: $oldChannelId -> $channelId")
                
                // Create new conversation with correct channel ID
                val migratedConversation = existingConversation.copy(channelId = channelId)
                
                // Delete old conversation and insert new one
                conversationDao.deleteConversation(oldChannelId)
                conversationDao.insertConversation(migratedConversation)
                
                // Update all messages to use new channel ID
                chatMessageDao.updateChannelId(oldChannelId, channelId)
                
                android.util.Log.d("ChatRepository", "Successfully migrated conversation: $oldChannelId -> $channelId")
                return Result.Success(migratedConversation)
            }

            // Get usernames for new conversation
            val user1Result = userRepository.getUserById(user1Id)
            val user2Result = userRepository.getUserById(user2Id)
            
            if (user1Result is com.example.ecosort.data.model.Result.Success && 
                user2Result is com.example.ecosort.data.model.Result.Success) {
                
                val user1 = user1Result.data
                val user2 = user2Result.data
                
                // Create new conversation
                val conversation = Conversation(
                    channelId = channelId,
                    participant1Id = if (user1Id < user2Id) user1Id else user2Id,
                    participant1Username = if (user1Id < user2Id) user1.username else user2.username,
                    participant2Id = if (user1Id < user2Id) user2Id else user1Id,
                    participant2Username = if (user1Id < user2Id) user2.username else user1.username
                )

                conversationDao.insertConversation(conversation)
                android.util.Log.d("ChatRepository", "Created new conversation: $channelId")
                Result.Success(conversation)
            } else {
                Result.Error(Exception("Could not get user information"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error in getOrCreateConversation", e)
            Result.Error(e)
        }
    }

    /**
     * Update conversation with new message
     */
    suspend fun updateConversationWithMessage(channelId: String, messageText: String, senderId: Long): Result<Unit> {
        return try {
            conversationDao.updateLastMessage(channelId, messageText, System.currentTimeMillis(), senderId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    suspend fun deleteConversation(channelId: String): Result<Unit> {
        return try {
            // Delete all messages in the conversation
            chatMessageDao.deleteChannelMessages(channelId)
            // Delete the conversation itself
            conversationDao.deleteConversation(channelId)
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error deleting conversation", e)
            Result.Error(e)
        }
    }
}
