package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.model.BroadcastChannel
import com.example.model.CreatorCommunity
import com.example.ui.channel.ChannelDetailScreen
import com.example.ui.channel.ChannelsScreen
import com.example.ui.chat.ChatDetailScreen
import com.example.ui.chat.ChatsScreen
import com.example.ui.community.CommunitiesScreen
import com.example.ui.community.CommunityDetailScreen
import com.example.ui.feed.SocialFeedScreen
import com.example.ui.profile.ProfileAndSafetyScreen
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ChannelViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.CommunityViewModel
import com.example.viewmodel.FeedViewModel
import com.example.viewmodel.SafetyViewModel

enum class MainTab(val title: String) {
    CHATS("Chats"),
    FEED("Feed"),
    COMMUNITIES("Communities"),
    CHANNELS("Channels"),
    PROFILE("Profile")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseMainApp(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    communityViewModel: CommunityViewModel,
    channelViewModel: ChannelViewModel,
    safetyViewModel: SafetyViewModel,
    onSignOut: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }
    var activeConversation by remember { mutableStateOf<Pair<String, String>?>(null) }
    var activeCommunity by remember { mutableStateOf<CreatorCommunity?>(null) }
    var activeChannel by remember { mutableStateOf<BroadcastChannel?>(null) }

    // If active conversation is open
    activeConversation?.let { (convoId, title) ->
        BackHandler { activeConversation = null }
        ChatDetailScreen(
            conversationId = convoId,
            conversationTitle = title,
            chatViewModel = chatViewModel,
            onBack = { activeConversation = null }
        )
        return
    }

    // If active community is open
    activeCommunity?.let { community ->
        BackHandler { activeCommunity = null }
        CommunityDetailScreen(
            community = community,
            viewModel = communityViewModel,
            onBack = { activeCommunity = null }
        )
        return
    }

    // If active channel is open
    activeChannel?.let { channel ->
        BackHandler { activeChannel = null }
        ChannelDetailScreen(
            channel = channel,
            viewModel = channelViewModel,
            onBack = { activeChannel = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedTab.title,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHATS,
                    onClick = { selectedTab = MainTab.CHATS },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    modifier = Modifier.testTag("tab_chats")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.FEED,
                    onClick = { selectedTab = MainTab.FEED },
                    icon = { Icon(Icons.Default.DynamicFeed, contentDescription = "Feed") },
                    label = { Text("Feed") },
                    modifier = Modifier.testTag("tab_feed")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.COMMUNITIES,
                    onClick = { selectedTab = MainTab.COMMUNITIES },
                    icon = { Icon(Icons.Default.Groups, contentDescription = "Communities") },
                    label = { Text("Communities") },
                    modifier = Modifier.testTag("tab_communities")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHANNELS,
                    onClick = { selectedTab = MainTab.CHANNELS },
                    icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Channels") },
                    label = { Text("Channels") },
                    modifier = Modifier.testTag("tab_channels")
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.PROFILE,
                    onClick = { selectedTab = MainTab.PROFILE },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    modifier = Modifier.testTag("tab_profile")
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                MainTab.CHATS -> ChatsScreen(
                    chatViewModel = chatViewModel,
                    onOpenConversation = { id, title ->
                        activeConversation = Pair(id, title)
                    }
                )
                MainTab.FEED -> SocialFeedScreen(
                    feedViewModel = feedViewModel
                )
                MainTab.COMMUNITIES -> CommunitiesScreen(
                    communityViewModel = communityViewModel,
                    onSelectCommunity = { activeCommunity = it }
                )
                MainTab.CHANNELS -> ChannelsScreen(
                    channelViewModel = channelViewModel,
                    onSelectChannel = { activeChannel = it }
                )
                MainTab.PROFILE -> ProfileAndSafetyScreen(
                    authViewModel = authViewModel,
                    safetyViewModel = safetyViewModel,
                    onSignOutComplete = onSignOut
                )
            }
        }
    }
}
