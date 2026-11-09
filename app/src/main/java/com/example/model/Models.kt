package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    val isPrivate: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    val conversationId: String = "",
    val type: String = "DIRECT", // "DIRECT" or "GROUP"
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val title: String = "",
    val iconUrl: String = "",
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    val messageId: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val content: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "", // "IMAGE", "AUDIO", "NONE"
    val status: String = "SENT", // "SENT", "DELIVERED", "READ"
    val readBy: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SocialPost(
    val postId: String = "",
    val authorUid: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val caption: String = "",
    val mediaUrls: List<String> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val likedByMe: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CreatorCommunity(
    val communityId: String = "",
    val creatorUid: String = "",
    val name: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val membersCount: Long = 1L,
    val isPrivate: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CommunityPost(
    val postId: String = "",
    val communityId: String = "",
    val authorUid: String = "",
    val authorUsername: String = "",
    val content: String = "",
    val mediaUrls: List<String> = emptyList(),
    val likesCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BroadcastChannel(
    val channelId: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val description: String = "",
    val iconUrl: String = "",
    val subscribersCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChannelBroadcastPost(
    val postId: String = "",
    val channelId: String = "",
    val authorUid: String = "",
    val content: String = "",
    val mediaUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserBlock(
    val blockId: String = "",
    val blockerUid: String = "",
    val blockedUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContentReport(
    val reportId: String = "",
    val reporterUid: String = "",
    val targetType: String = "", // "POST", "MESSAGE", "USER", "COMMUNITY"
    val targetId: String = "",
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
