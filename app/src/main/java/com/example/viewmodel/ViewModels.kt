package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.BroadcastChannel
import com.example.model.ChannelBroadcastPost
import com.example.model.ChatMessage
import com.example.model.CommunityPost
import com.example.model.Conversation
import com.example.model.CreatorCommunity
import com.example.model.SocialPost
import com.example.model.UserBlock
import com.example.model.UserProfile
import com.example.repository.IAuthRepository
import com.example.repository.IChannelRepository
import com.example.repository.IChatRepository
import com.example.repository.ICommunityRepository
import com.example.repository.ISafetyRepository
import com.example.repository.ISocialFeedRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AuthViewModel(private val repository: IAuthRepository) : ViewModel() {
    val currentUserProfile: StateFlow<UserProfile?> = repository.observeCurrentUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun completeProfileSetup(username: String, displayName: String, bio: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSavingProfile.value = true
            _errorMessage.value = null

            val available = repository.checkUsernameAvailable(username)
            if (!available) {
                _errorMessage.value = "Username @$username is already taken"
                _isSavingProfile.value = false
                return@launch
            }

            val result = repository.saveUserProfile(username, displayName, bio)
            _isSavingProfile.value = false
            if (result.isSuccess) {
                onComplete()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Failed to save profile"
            }
        }
    }
}

class ChatViewModel(private val repository: IChatRepository) : ViewModel() {
    val conversations: StateFlow<UiState<List<Conversation>>> = repository.observeConversations()
        .catch { emit(emptyList()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UiState.Loading
        ).let { base ->
            val flow = MutableStateFlow<UiState<List<Conversation>>>(UiState.Loading)
            viewModelScope.launch {
                repository.observeConversations().catch { e ->
                    flow.value = UiState.Error(e.message ?: "Failed to load chats")
                }.collect {
                    flow.value = UiState.Success(it)
                }
            }
            flow.asStateFlow()
        }

    private val _activeMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val activeMessages: StateFlow<List<ChatMessage>> = _activeMessages.asStateFlow()

    fun selectConversation(conversationId: String) {
        viewModelScope.launch {
            repository.observeMessages(conversationId).catch {
                _activeMessages.value = emptyList()
            }.collect {
                _activeMessages.value = it
            }
        }
    }

    fun sendMessage(conversationId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(conversationId, text.trim())
        }
    }

    fun startDirectChat(targetUid: String, targetName: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.createDirectConversation(targetUid, targetName)
            result.getOrNull()?.let(onReady)
        }
    }

    fun createGroupChat(title: String, memberUids: List<String>, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.createGroupConversation(title, memberUids)
            result.getOrNull()?.let(onReady)
        }
    }
}

class FeedViewModel(private val repository: ISocialFeedRepository) : ViewModel() {
    private val _feedState = MutableStateFlow<UiState<List<SocialPost>>>(UiState.Loading)
    val feedState: StateFlow<UiState<List<SocialPost>>> = _feedState.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            repository.observeFeed().catch { e ->
                _feedState.value = UiState.Error(e.message ?: "Failed to load feed")
            }.collect {
                _feedState.value = UiState.Success(it)
            }
        }
    }

    fun publishPost(caption: String, onComplete: () -> Unit) {
        if (caption.isBlank()) return
        viewModelScope.launch {
            val result = repository.createPost(caption)
            if (result.isSuccess) {
                onComplete()
            }
        }
    }

    fun toggleLike(postId: String, currentLikes: Long, liked: Boolean) {
        viewModelScope.launch {
            repository.toggleLike(postId, currentLikes, liked)
        }
    }

    fun followUser(targetUid: String) {
        viewModelScope.launch {
            repository.followUser(targetUid)
        }
    }
}

class CommunityViewModel(private val repository: ICommunityRepository) : ViewModel() {
    private val _communities = MutableStateFlow<UiState<List<CreatorCommunity>>>(UiState.Loading)
    val communities: StateFlow<UiState<List<CreatorCommunity>>> = _communities.asStateFlow()

    private val _communityPosts = MutableStateFlow<List<CommunityPost>>(emptyList())
    val communityPosts: StateFlow<List<CommunityPost>> = _communityPosts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCommunities().catch { e ->
                _communities.value = UiState.Error(e.message ?: "Failed to load communities")
            }.collect {
                _communities.value = UiState.Success(it)
            }
        }
    }

    fun selectCommunity(communityId: String) {
        viewModelScope.launch {
            repository.observeCommunityPosts(communityId).catch {
                _communityPosts.value = emptyList()
            }.collect {
                _communityPosts.value = it
            }
        }
    }

    fun createCommunity(name: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = repository.createCommunity(name, description)
            if (result.isSuccess) onDone()
        }
    }

    fun joinCommunity(communityId: String) {
        viewModelScope.launch {
            repository.joinCommunity(communityId)
        }
    }

    fun createCommunityPost(communityId: String, content: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = repository.createCommunityPost(communityId, content)
            if (result.isSuccess) onDone()
        }
    }
}

class ChannelViewModel(private val repository: IChannelRepository) : ViewModel() {
    private val _channels = MutableStateFlow<UiState<List<BroadcastChannel>>>(UiState.Loading)
    val channels: StateFlow<UiState<List<BroadcastChannel>>> = _channels.asStateFlow()

    private val _channelPosts = MutableStateFlow<List<ChannelBroadcastPost>>(emptyList())
    val channelPosts: StateFlow<List<ChannelBroadcastPost>> = _channelPosts.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeChannels().catch { e ->
                _channels.value = UiState.Error(e.message ?: "Failed to load channels")
            }.collect {
                _channels.value = UiState.Success(it)
            }
        }
    }

    fun selectChannel(channelId: String) {
        viewModelScope.launch {
            repository.observeChannelPosts(channelId).catch {
                _channelPosts.value = emptyList()
            }.collect {
                _channelPosts.value = it
            }
        }
    }

    fun createChannel(name: String, description: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = repository.createChannel(name, description)
            if (result.isSuccess) onDone()
        }
    }

    fun subscribe(channelId: String) {
        viewModelScope.launch {
            repository.subscribeChannel(channelId)
        }
    }

    fun publishBroadcast(channelId: String, content: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = repository.publishChannelPost(channelId, content)
            if (result.isSuccess) onDone()
        }
    }
}

class SafetyViewModel(private val repository: ISafetyRepository) : ViewModel() {
    val blockedUsers: StateFlow<List<UserBlock>> = repository.observeBlockedUsers()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun block(targetUid: String) {
        viewModelScope.launch {
            repository.blockUser(targetUid)
        }
    }

    fun unblock(targetUid: String) {
        viewModelScope.launch {
            repository.unblockUser(targetUid)
        }
    }

    fun report(targetType: String, targetId: String, reason: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val result = repository.reportContent(targetType, targetId, reason)
            if (result.isSuccess) onDone()
        }
    }
}
