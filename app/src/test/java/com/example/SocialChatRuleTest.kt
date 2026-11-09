package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.repository.AuthRepository
import com.example.repository.ChannelRepository
import com.example.repository.ChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocialChatRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun userProfile_validSave_createsUserAndUsername(): Unit = runBlocking {
        val uniqueUser = "u_" + java.util.UUID.randomUUID().toString().take(8)
        val uid = signInTestUser("$uniqueUser@pulse.test")
        val authRepo = AuthRepository(firestore)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.saveUserProfile(uniqueUser, "Alice Tester", "Testing Pulse")
        }
        assertTrue("Save profile failed: " + result.exceptionOrNull(), result.isSuccess)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            authRepo.observeCurrentUserProfile().first()
        }
        assertEquals(uniqueUser, profile?.username)
    }

    @Test
    fun conversation_memberCanSendMessage(): Unit = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val chatRepo = ChatRepository(firestore)

        val convoResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.createDirectConversation("bob_uid", "Bob Jones")
        }
        assertTrue("createDirectConversation failed: " + convoResult.exceptionOrNull(), convoResult.isSuccess)
        val convoId = convoResult.getOrThrow()

        val sendResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            chatRepo.sendMessage(convoId, "Hello Bob!")
        }
        assertTrue(sendResult.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            chatRepo.observeMessages(convoId).first()
        }
        assertTrue(messages.any { it.content == "Hello Bob!" })
    }

    @Test
    fun channel_onlyOwnerCanBroadcast(): Unit = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceChannelRepo = ChannelRepository(firestore)

        val channelResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChannelRepo.createChannel("Alice News", "Official announcements")
        }
        assertTrue(channelResult.isSuccess)
        val channelId = channelResult.getOrThrow()

        // Alice (owner) can post
        val postResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceChannelRepo.publishChannelPost(channelId, "Important update!")
        }
        assertTrue(postResult.isSuccess)

        // Bob (non-owner) fails to post
        signInTestUser(BOB_EMAIL)
        val bobChannelRepo = ChannelRepository(firestore)
        val failResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobChannelRepo.publishChannelPost(channelId, "Unauthorized message")
        }
        assertTrue(failResult.isFailure)
    }

    @Test
    fun unauthenticatedAccess_failsWithPermissionDenied(): Unit = runBlocking {
        auth.signOut()
        val chatRepo = ChatRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                chatRepo.observeConversations().first()
            }
            fail("Expected exception when unauthenticated")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@pulse.test"
        const val BOB_EMAIL = "bob@pulse.test"
        const val DEFAULT_TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
