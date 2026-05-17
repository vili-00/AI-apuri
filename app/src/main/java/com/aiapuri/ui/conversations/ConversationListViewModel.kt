package com.aiapuri.ui.conversations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiapuri.core.model.Conversation
import com.aiapuri.core.model.ConversationSummary
import com.aiapuri.data.conversation.ConversationRepository
import com.aiapuri.data.persona.PersonaRepository
import com.aiapuri.data.settings.SettingsRepository
import com.aiapuri.core.util.TitleGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * UI state for the conversation list screen.
 */
data class ConversationListUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val searchQuery: String = "",
    val isCreating: Boolean = false,
    val isRenaming: Boolean = false,
    val renameConversationId: String? = null,
    val renameTitle: String = "",
    val deleteConfirmId: String? = null,
    val errorMessage: String? = null
)

/**
 * ViewModel managing conversation list state.
 *
 * Observes ConversationRepository reactively and provides
 * create, rename, delete, and search operations.
 */
class ConversationListViewModel(
    private val conversationRepository: ConversationRepository,
    private val personaRepository: PersonaRepository,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    var uiState by mutableStateOf(ConversationListUiState())
        private set

    /** Reactive conversation summaries from the repository. */
    private val conversationFlow = conversationRepository.observeConversationSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Start observing conversations. */
    init {
        viewModelScope.launch {
            conversationFlow.collect { summaries ->
                uiState = uiState.copy(conversations = summaries)
            }
        }
    }

    /** Filter conversations by search query. */
    val filteredConversations: List<ConversationSummary>
        get() {
            val query = uiState.searchQuery.trim().lowercase()
            if (query.isEmpty()) return uiState.conversations
            return uiState.conversations.filter { conv ->
                conv.title.lowercase().contains(query) ||
                conv.lastMessagePreview?.lowercase()?.contains(query) == true ||
                conv.model?.lowercase()?.contains(query) == true ||
                conv.personaName?.lowercase()?.contains(query) == true
            }
        }

    /** Update search query. */
    fun onSearchQueryChanged(query: String) {
        uiState = uiState.copy(searchQuery = query)
    }

    /**
     * Create a new conversation using the default model from settings.
     */
    fun createConversation() {
        viewModelScope.launch {
            try {
                // Get default model from settings
                val defaultModel = settingsRepository?.let { repo ->
                    repo.serverSettingsFlow.first().defaultModel
                }?.takeIf { it.isNotBlank() } ?: "default"

                val newConv = Conversation(
                    id = UUID.randomUUID().toString(),
                    title = TitleGenerator.DEFAULT_TITLE,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    model = defaultModel
                )
                conversationRepository.createConversation(newConv)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to create conversation: ${e.message}")
            }
        }
    }

    /** Start renaming a conversation. */
    fun startRename(conversationId: String, currentTitle: String) {
        uiState = uiState.copy(
            isRenaming = true,
            renameConversationId = conversationId,
            renameTitle = currentTitle,
            deleteConfirmId = null
        )
    }

    /** Update rename title. */
    fun onRenameTitleChanged(title: String) {
        uiState = uiState.copy(renameTitle = title)
    }

    /** Confirm rename. */
    fun confirmRename() {
        val id = uiState.renameConversationId ?: return
        val title = uiState.renameTitle.trim()
        if (title.isEmpty()) {
            uiState = uiState.copy(errorMessage = "Title cannot be empty")
            return
        }
        viewModelScope.launch {
            try {
                conversationRepository.updateConversationTitle(id, title)
                uiState = uiState.copy(
                    isRenaming = false,
                    renameConversationId = null,
                    renameTitle = ""
                )
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to rename: ${e.message}")
            }
        }
    }

    /** Cancel rename. */
    fun cancelRename() {
        uiState = uiState.copy(
            isRenaming = false,
            renameConversationId = null,
            renameTitle = ""
        )
    }

    /** Show delete confirmation for a conversation. */
    fun confirmDelete(conversationId: String) {
        uiState = uiState.copy(
            deleteConfirmId = conversationId,
            isRenaming = false,
            renameConversationId = null,
            renameTitle = ""
        )
    }

    /** Cancel delete confirmation. */
    fun cancelDelete() {
        uiState = uiState.copy(deleteConfirmId = null)
    }

    /** Delete a conversation. */
    fun deleteConversation() {
        val id = uiState.deleteConfirmId ?: return
        viewModelScope.launch {
            try {
                conversationRepository.deleteConversation(id)
                uiState = uiState.copy(deleteConfirmId = null)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to delete: ${e.message}")
            }
        }
    }

    /** Dismiss error message. */
    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
