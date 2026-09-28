package com.syncro.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.data.preferences.NotificationPreferences
import com.syncro.domain.model.AssistantConversation
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.assistantConversation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AssistantUiState(val conversation: AssistantConversation, val unreadCount: Int)

/**
 * Chat del asistente y número de mensajes sin leer. MainScaffold crea una sola instancia y la
 * comparte con la pantalla, así el número de la barra y el chat nunca se desincronizan.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val preferences: NotificationPreferences
) : ViewModel() {

    // Lo decide Android (permiso, notificaciones silenciadas): la pantalla lo comprueba y lo pasa
    private val notificationsAllowed = MutableStateFlow<Boolean?>(null)

    // null hasta saber el permiso: evita enseñar la pregunta (y el número) un instante por error
    val uiState: StateFlow<AssistantUiState?> = combine(
        preferences.digestAnswer,
        preferences.readMessageIds,
        preferences.clearedMessageIds,
        notificationsAllowed.filterNotNull()
    ) { answer, readIds, clearedIds, allowed ->
        val conversation = assistantConversation(answer, allowed, clearedIds)
        AssistantUiState(conversation, conversation.unreadCount(readIds))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onNotificationsAllowedChanged(allowed: Boolean) {
        notificationsAllowed.value = allowed
    }

    fun answerDigest(answer: DigestAnswer) {
        viewModelScope.launch { preferences.saveDigestAnswer(answer) }
    }

    /** El chat está en pantalla: todo lo que hay en él se da por leído. */
    fun markAllRead() {
        val ids = visibleMessageIds() ?: return
        viewModelScope.launch { preferences.markRead(ids) }
    }

    /** Vacía el chat: los mensajes actuales desaparecen; los que lleguen después sí se verán. */
    fun clearChat() {
        val ids = visibleMessageIds() ?: return
        viewModelScope.launch { preferences.clearMessages(ids) }
    }

    private fun visibleMessageIds(): Set<String>? =
        uiState.value?.conversation?.messages?.map { it.id }?.toSet()
}
