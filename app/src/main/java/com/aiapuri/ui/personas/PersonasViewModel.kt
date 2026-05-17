package com.aiapuri.ui.personas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiapuri.core.model.Persona
import com.aiapuri.data.persona.PersonaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * UI state for the personas screen.
 */
data class PersonasUiState(
    val personas: List<Persona> = emptyList(),
    val errorMessage: String? = null
)

/**
 * ViewModel for the personas management screen.
 */
class PersonasViewModel(
    private val personaRepository: PersonaRepository
) : ViewModel() {

    var uiState by mutableStateOf(PersonasUiState())
        private set

    private val personaFlow = personaRepository.observeAllPersonas()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            personaFlow.collect { personas ->
                uiState = uiState.copy(personas = personas)
            }
        }
    }

    /** Create a new blank persona. */
    fun createPersona(): Persona {
        val newPersona = Persona(
            id = UUID.randomUUID().toString(),
            name = "New Persona",
            description = "",
            systemPrompt = ""
        )
        viewModelScope.launch {
            try {
                personaRepository.savePersona(newPersona)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to create persona: ${e.message}")
            }
        }
        return newPersona
    }

    /** Update an existing persona. */
    fun updatePersona(persona: Persona) {
        viewModelScope.launch {
            try {
                personaRepository.savePersona(persona)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to update persona: ${e.message}")
            }
        }
    }

    /** Delete a persona. */
    fun deletePersona(id: String) {
        viewModelScope.launch {
            try {
                personaRepository.deletePersona(id)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to delete persona: ${e.message}")
            }
        }
    }

    /** Set a persona as the default. */
    fun setDefaultPersona(id: String) {
        viewModelScope.launch {
            try {
                personaRepository.setDefaultPersona(id)
            } catch (e: Exception) {
                uiState = uiState.copy(errorMessage = "Failed to set default: ${e.message}")
            }
        }
    }

    /** Get the default persona. */
    fun getDefaultPersonaId(): String? {
        return uiState.personas.firstOrNull { it.isDefault }?.id
    }

    /** Dismiss error. */
    fun dismissError() {
        uiState = uiState.copy(errorMessage = null)
    }
}
