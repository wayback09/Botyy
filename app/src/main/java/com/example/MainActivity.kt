package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.repository.AuthRepository
import com.example.repository.ChannelRepository
import com.example.repository.ChatRepository
import com.example.repository.CommunityRepository
import com.example.repository.SafetyRepository
import com.example.repository.SocialFeedRepository
import com.example.ui.AuthGateScreen
import com.example.ui.PulseMainApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ChannelViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.CommunityViewModel
import com.example.viewmodel.FeedViewModel
import com.example.viewmodel.SafetyViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dbId = getString(R.string.firestore_database_id)
        val firestore = FirebaseFirestore.getInstance(dbId)

        val authRepo = AuthRepository(firestore)
        val chatRepo = ChatRepository(firestore)
        val feedRepo = SocialFeedRepository(firestore)
        val communityRepo = CommunityRepository(firestore)
        val channelRepo = ChannelRepository(firestore)
        val safetyRepo = SafetyRepository(firestore)

        setContent {
            MyApplicationTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        authRepo = authRepo,
                        chatRepo = chatRepo,
                        feedRepo = feedRepo,
                        communityRepo = communityRepo,
                        channelRepo = channelRepo,
                        safetyRepo = safetyRepo
                    )
                }
            }
        }
    }
}

@Composable
fun AppRoot(
    authRepo: AuthRepository,
    chatRepo: ChatRepository,
    feedRepo: SocialFeedRepository,
    communityRepo: CommunityRepository,
    channelRepo: ChannelRepository,
    safetyRepo: SafetyRepository
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    val authViewModel = remember { AuthViewModel(authRepo) }

    AuthGateScreen(authViewModel = authViewModel) {
        currentUser?.let { user ->
            val uid = user.uid
            key(uid) {
                val chatViewModel = remember(uid) { ChatViewModel(chatRepo) }
                val feedViewModel = remember(uid) { FeedViewModel(feedRepo) }
                val communityViewModel = remember(uid) { CommunityViewModel(communityRepo) }
                val channelViewModel = remember(uid) { ChannelViewModel(channelRepo) }
                val safetyViewModel = remember(uid) { SafetyViewModel(safetyRepo) }

                PulseMainApp(
                    authViewModel = authViewModel,
                    chatViewModel = chatViewModel,
                    feedViewModel = feedViewModel,
                    communityViewModel = communityViewModel,
                    channelViewModel = channelViewModel,
                    safetyViewModel = safetyViewModel,
                    onSignOut = {
                        currentUser = null
                    }
                )
            }
        }
    }
}
