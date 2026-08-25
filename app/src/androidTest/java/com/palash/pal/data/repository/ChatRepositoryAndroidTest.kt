package com.palash.pal.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.palash.pal.core.time.TimeProvider
import com.palash.pal.data.local.db.PalDatabase
import com.palash.pal.domain.model.MessageRole
import com.palash.pal.domain.model.MessageStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatRepositoryAndroidTest {
    private lateinit var database: PalDatabase
    private lateinit var repository: ChatRepositoryImpl
    private lateinit var timeProvider: TestTimeProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        timeProvider = TestTimeProvider()
        database = Room.inMemoryDatabaseBuilder(context, PalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ChatRepositoryImpl(database, timeProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun storesConversationAndMessagesInOrder() = runTest {
        val conversation = repository.createConversation("PAL")
        val firstMessage = repository.createUserMessage(conversation.id, "Hello PAL")
        val assistantMessage = repository.createAssistantMessage(
            conversationId = conversation.id,
            content = "Hello back",
            status = MessageStatus.COMPLETED
        )

        val messages = repository.getMessages(conversation.id)

        assertEquals(conversation.id, firstMessage.conversationId)
        assertEquals(2, messages.size)
        assertEquals(MessageRole.USER, messages[0].role)
        assertEquals(MessageRole.ASSISTANT, messages[1].role)
        assertEquals(assistantMessage.id, messages[1].id)
    }

    @Test
    fun deletesMessagesWhenConversationIsDeleted() = runTest {
        val conversation = repository.createConversation("PAL")
        repository.createUserMessage(conversation.id, "Hello PAL")

        repository.deleteConversation(conversation.id)

        assertTrue(repository.getMessages(conversation.id).isEmpty())
        assertTrue(repository.getActiveConversation() == null)
    }

    @Test
    fun observeMessagesEmitsUpdates() = runTest {
        val conversation = repository.createConversation("PAL")
        val collected = async(start = CoroutineStart.UNDISPATCHED) {
            repository.observeMessages(conversation.id).take(2).toList()
        }

        repository.createUserMessage(conversation.id, "Hello PAL")

        val emissions = collected.await()
        assertEquals(2, emissions.size)
        assertTrue(emissions.last().isNotEmpty())
    }

    @Test
    fun observesConversationsInDescendingUpdateOrder() = runTest {
        val firstConversation = repository.createConversation("First")
        val secondConversation = repository.createConversation("Second")

        val conversations = repository.observeConversations().first()

        assertEquals(secondConversation.id, conversations.first().id)
        assertEquals(firstConversation.id, conversations.last().id)
    }

    private class TestTimeProvider : TimeProvider {
        private var currentTime = 1_000L

        override fun nowMillis(): Long {
            currentTime += 1_000L
            return currentTime
        }
    }
}
