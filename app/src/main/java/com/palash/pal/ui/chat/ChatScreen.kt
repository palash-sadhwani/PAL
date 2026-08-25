package com.palash.pal.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.palash.pal.R
import com.palash.pal.di.AppContainer
import com.palash.pal.domain.model.Message
import com.palash.pal.domain.model.MessageRole

@Composable
fun ChatRoute(
    appContainer: AppContainer,
    onOpenSettings: () -> Unit
) {
    val viewModel: ChatViewModel = viewModel(factory = appContainer.chatViewModelFactory())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Model download / consent state
    val modelBundle = appContainer.localModelBundle
    val initialAvailable = remember { modelBundle.resolveIfAvailable() != null }
    val showConsent = remember { androidx.compose.runtime.mutableStateOf(!initialAvailable) }
    val downloadInProgress = remember { androidx.compose.runtime.mutableStateOf(false) }
    val downloadProgress = remember { androidx.compose.runtime.mutableStateOf(0) }
    val downloadCompleted = remember { androidx.compose.runtime.mutableStateOf(false) }
    val downloadFailed = remember { androidx.compose.runtime.mutableStateOf(false) }

    // Helper to post updates on main thread
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun startDownload() {
        downloadInProgress.value = true
        downloadFailed.value = false
        downloadCompleted.value = false
        modelBundle.startBackgroundDownload(progressCallback = { pct ->
            mainHandler.post { downloadProgress.value = pct }
        }, completionCallback = { success, path ->
            mainHandler.post {
                downloadInProgress.value = false
                downloadCompleted.value = success
                downloadFailed.value = !success
                showConsent.value = false
            }
        })
    }

    ChatScreen(
        uiState = uiState,
        onDraftChanged = viewModel::onDraftChanged,
        onSendClicked = viewModel::onSendClicked,
        onOpenSettings = onOpenSettings
    )

    if (showConsent.value) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* don't dismiss */ },
            title = { Text("Download on-device model?") },
            text = { Text("PAL can download a local GGUF model (~few hundred MB). This enables offline inference but may use significant storage and mobile data. Continue?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { startDownload() }) {
                    Text("Download")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConsent.value = false }) {
                    Text("Later")
                }
            }
        )
    }

    if (downloadInProgress.value) {
        // Overlay progress
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Card(modifier = Modifier.padding(24.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Downloading model... ${downloadProgress.value}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator(progress = (downloadProgress.value / 100f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        androidx.compose.material3.TextButton(onClick = {
                            // cancel download
                            modelBundle.cancelDownload()
                            downloadInProgress.value = false
                            downloadFailed.value = true
                        }) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
    }

    if (downloadCompleted.value) {
        // small non-blocking banner - using ErrorBanner for simplicity
        androidx.compose.runtime.LaunchedEffect(Unit) {
            // no-op, banner will appear in ChatScreen as success message could be implemented
        }
    }

    if (downloadFailed.value) {
        // show a simple error banner overlaying the chat
        androidx.compose.ui.window.Dialog(onDismissRequest = { downloadFailed.value = false }) {
            Card(modifier = Modifier.padding(24.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Model download failed. You can retry from settings.")
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.TextButton(onClick = {
                        downloadFailed.value = false
                        showConsent.value = true
                    }) { Text("Retry") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.conversationTitle.isNotBlank()) {
                            Text(
                                text = uiState.conversationTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.chat_open_settings)
                        )
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                draftMessage = uiState.draftMessage,
                isLoading = uiState.isLoading,
                onDraftChanged = onDraftChanged,
                onSendClicked = onSendClicked
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    LoadingState(modifier = Modifier.align(Alignment.Center))
                }

                uiState.messages.isEmpty() -> {
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    MessageList(
                        messages = uiState.messages,
                        listState = listState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (uiState.errorMessageRes != null) {
                ErrorBanner(
                    text = stringResource(uiState.errorMessageRes),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message)
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUserMessage = message.role == MessageRole.USER
    val horizontalAlignment = if (isUserMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isUserMessage) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isUserMessage) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = bubbleColor
        ) {
            Text(
                text = message.content.ifBlank {
                    stringResource(R.string.chat_placeholder_generating)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = contentColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.chat_loading))
    }
}

@Composable
private fun ErrorBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    draftMessage: String,
    isLoading: Boolean,
    onDraftChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.navigationBarsPadding(),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draftMessage,
                onValueChange = onDraftChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                enabled = !isLoading,
                singleLine = false,
                maxLines = 4
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClicked,
                enabled = !isLoading && draftMessage.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send)
                )
            }
        }
    }
}
