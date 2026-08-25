package com.palash.pal.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.palash.pal.agent.DefaultAgentRuntime
import com.palash.pal.ai.LocalModelBundle
import com.palash.pal.ai.OnDeviceAIEngine
import com.palash.pal.ai.PlaceholderAIEngine
import com.palash.pal.core.time.SystemTimeProvider
import com.palash.pal.data.local.db.PalDatabase
import com.palash.pal.data.repository.ChatRepositoryImpl
import com.palash.pal.domain.agent.AgentRuntime
import com.palash.pal.domain.repository.ChatRepository
import com.palash.pal.domain.tools.ToolPermissionPolicy
import com.palash.pal.domain.tools.ToolRegistry
import com.palash.pal.tools.InMemoryToolRegistry
import com.palash.pal.tools.NoOpPermissionPolicy
import androidx.room.Room

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val timeProvider = SystemTimeProvider()

    val database: PalDatabase = Room.databaseBuilder(
        appContext,
        PalDatabase::class.java,
        DATABASE_NAME
    ).build()

    val chatRepository: ChatRepository = ChatRepositoryImpl(
        database = database,
        timeProvider = timeProvider
    )

    val localModelBundle = LocalModelBundle(appContext)

    val localAIEngine = OnDeviceAIEngine(
        modelPath = null,
        modelResolver = { localModelBundle.resolveIfAvailable() ?: localModelBundle.targetModelPath() },
        fallbackEngine = PlaceholderAIEngine()
    )

    val agentRuntime: AgentRuntime = DefaultAgentRuntime(localAIEngine)

    val toolRegistry: ToolRegistry = InMemoryToolRegistry()

    val toolPermissionPolicy: ToolPermissionPolicy = NoOpPermissionPolicy()

    fun chatViewModelFactory(): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(com.palash.pal.ui.chat.ChatViewModel::class.java)) {
                    return com.palash.pal.ui.chat.ChatViewModel(
                        chatRepository = chatRepository,
                        agentRuntime = agentRuntime
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }

    private companion object {
        const val DATABASE_NAME = "pal.db"
    }
}
