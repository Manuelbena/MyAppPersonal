package com.syncro.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.syncro.data.preferences.AssistantPreferences
import com.syncro.domain.model.AssistantConversation
import com.syncro.domain.model.BudgetAlert
import com.syncro.domain.model.ChatReply
import com.syncro.domain.model.DailyFocus
import com.syncro.domain.model.DigestAnswer
import com.syncro.domain.model.FocusCandidates
import com.syncro.domain.model.LeftoverChoice
import com.syncro.domain.model.LeftoverOutcome
import com.syncro.domain.model.LeftoverTasks
import com.syncro.domain.model.MoveTarget
import com.syncro.domain.model.Payday
import com.syncro.domain.model.PaydayAnswer
import com.syncro.domain.model.TaskAction
import com.syncro.domain.model.assistantConversation
import com.syncro.domain.model.budgetAlerts
import com.syncro.domain.model.lastPayday
import com.syncro.domain.model.salaryCents
import com.syncro.domain.usecase.ChooseDailyFocusUseCase
import com.syncro.domain.usecase.GetFocusCandidatesUseCase
import com.syncro.domain.usecase.GetFocusHistoryUseCase
import com.syncro.domain.usecase.GetBudgetsUseCase
import com.syncro.domain.usecase.ObserveSavingsAccountsUseCase
import com.syncro.domain.usecase.GetLeftoverTasksUseCase
import com.syncro.domain.usecase.GetMonthMovementsUseCase
import com.syncro.domain.usecase.GetSettingsUseCase
import com.syncro.domain.usecase.MoveTasksUseCase
import com.syncro.domain.usecase.ToggleTaskCompletionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class AssistantUiState(val conversation: AssistantConversation, val unreadCount: Int)

/**
 * Chat del asistente y número de mensajes sin leer. MainScaffold crea una sola instancia y la
 * comparte con la pantalla, así el número de la barra y el chat nunca se desincronizan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val preferences: AssistantPreferences,
    private val getLeftoverTasks: GetLeftoverTasksUseCase,
    private val moveTasks: MoveTasksUseCase,
    private val toggleTaskCompletion: ToggleTaskCompletionUseCase,
    private val getFocusCandidates: GetFocusCandidatesUseCase,
    private val getFocusHistory: GetFocusHistoryUseCase,
    private val chooseDailyFocus: ChooseDailyFocusUseCase,
    private val getMonthMovements: GetMonthMovementsUseCase,
    getBudgets: GetBudgetsUseCase,
    observeAccounts: ObserveSavingsAccountsUseCase,
    getSettings: GetSettingsUseCase,
    private val clock: Clock
) : ViewModel() {

    // Lo decide Android (permiso, notificaciones silenciadas): la pantalla lo comprueba y lo pasa
    private val notificationsAllowed = MutableStateFlow<Boolean?>(null)

    // Cambia al volver a la app: el repaso y las prioridades dependen de la hora (antes o después de las 21:00)
    private val refreshTick = MutableStateFlow(0)
    private val leftovers: StateFlow<LeftoverTasks?> = refreshTick
        .flatMapLatest { getLeftoverTasks() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Envuelto porque null ("ya no toca preguntar") es un valor válido, distinto de "aún no se sabe"
    private data class Focus(val candidates: FocusCandidates?)
    private val focus: StateFlow<Focus?> = refreshTick
        .flatMapLatest { getFocusCandidates() }
        .map { Focus(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Solo la última semana: el chat no crece sin fin
    private val historySince get() = LocalDate.now(clock).minusDays(HISTORY_DAYS)
    private val focusHistory = refreshTick.flatMapLatest { getFocusHistory(historySince) }

    // Día de nómina: el último, si fue esta semana, con la nómina apuntada ese mes y lo contestado.
    // Envuelto como Focus: null (sin nómina) es válido y distinto de "aún no se sabe"
    private data class PaydayState(val payday: Payday?)
    private val payday: StateFlow<PaydayState?> = combine(refreshTick, getSettings(), preferences.paydayAnswers) { _, settings, answers ->
        settings.assistant.paydayDay to answers
    }.flatMapLatest { (day, answers) ->
        val date = day?.let { lastPayday(it, LocalDate.now(clock)) }
        if (date == null || date.isBefore(historySince)) {
            flowOf(PaydayState(null))
        } else {
            // El mes que empieza con esa nómina
            getMonthMovements(date).map { month -> PaydayState(Payday(date, month.salaryCents(), answers[date])) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Avisos de presupuesto de esta semana: el mes actual y, a principios de mes, también el anterior
    // De todas las cuentas de ahorro: cada presupuesto con los gastos de su cuenta
    private val budgetAlerts = combine(refreshTick, getSettings(), getBudgets(), observeAccounts()) { _, settings, budgets, accounts ->
        budgets.takeIf { settings.assistant.budgetAlertsEnabled }.orEmpty() to accounts
    }.flatMapLatest { (budgets, accounts) ->
        if (budgets.isEmpty()) return@flatMapLatest flowOf(emptyList())
        val today = LocalDate.now(clock)
        val since = historySince
        // Los meses (o periodos de nómina) en que caen hace una semana y hoy; si son el mismo, una vez
        combine(listOf(since, today).map { getMonthMovements(it) }) { monthly ->
            monthly.distinctBy { it.period }
                .flatMap { it.budgetAlerts(budgets, today) }
                .filter { !it.date.isBefore(since) }
                // Con varias cuentas el mensaje dice de cuál es el presupuesto
                .map { alert -> if (accounts.hasSeveral) alert.copy(accountName = accounts.nameOf(alert.budget.accountId)) else alert }
        }
    }

    // Lo de dinero, junto: nómina y presupuestos
    private data class Money(val payday: Payday?, val budgetAlerts: List<BudgetAlert>)
    private val money = combine(payday.filterNotNull(), budgetAlerts) { payday, alerts -> Money(payday.payday, alerts) }

    private data class Tasks(val leftovers: LeftoverTasks, val focus: Focus, val focusHistory: List<DailyFocus>, val money: Money)
    private val tasks = combine(
        leftovers.filterNotNull(), focus.filterNotNull(), focusHistory, getSettings(), money
    ) { leftovers, focus, history, settings, money ->
        // Con una pregunta apagada en Ajustes no se hace; lo ya contestado sigue en el historial
        Tasks(
            leftovers = if (settings.assistant.leftoversEnabled) leftovers else leftovers.copy(tasks = emptyList()),
            focus = if (settings.assistant.focusEnabled) focus else Focus(null),
            focusHistory = history,
            money = money
        )
    }

    private val chatInputs = combine(
        preferences.digestAnswer,
        notificationsAllowed.filterNotNull(),
        tasks,
        preferences.leftoverOutcomes,
        preferences.clearedMessageIds
    ) { answer, allowed, tasks, outcomes, clearedIds ->
        assistantConversation(
            answer = answer,
            notificationsAllowed = allowed,
            leftovers = tasks.leftovers,
            outcomes = outcomes.filter { !it.reviewDate.isBefore(historySince) },
            focus = tasks.focus.candidates,
            focusHistory = tasks.focusHistory,
            payday = tasks.money.payday,
            budgetAlerts = tasks.money.budgetAlerts,
            clearedIds = clearedIds
        )
    }

    // null hasta saber el permiso y las pendientes: evita enseñar mensajes (y el número) por error
    val uiState: StateFlow<AssistantUiState?> = combine(chatInputs, preferences.readMessageIds) { conversation, readIds ->
        AssistantUiState(conversation, conversation.unreadCount(readIds))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Al volver a la app: permiso de avisos y repaso de pendientes (puede haber pasado de las 21:00). */
    fun onResume(notificationsAllowed: Boolean) {
        this.notificationsAllowed.value = notificationsAllowed
        refreshTick.value++
    }

    fun onNotificationsAllowedChanged(allowed: Boolean) {
        notificationsAllowed.value = allowed
    }

    fun answerDigest(answer: DigestAnswer) {
        viewModelScope.launch { preferences.saveDigestAnswer(answer) }
    }

    /** Opciones generales del repaso de pendientes. */
    fun answerLeftovers(reply: ChatReply) {
        val current = leftovers.value ?: return
        if (current.tasks.isEmpty()) return
        val choice = when (reply) {
            ChatReply.LEFTOVERS_MOVE_ALL -> LeftoverChoice.MOVE_ALL
            ChatReply.LEFTOVERS_ONE_BY_ONE -> LeftoverChoice.ONE_BY_ONE
            ChatReply.LEFTOVERS_KEEP -> LeftoverChoice.KEEP
            else -> return
        }
        val total = current.tasks.size
        viewModelScope.launch {
            // Primero la decisión y luego mover: si no, la pregunta desaparecería un instante
            preferences.saveLeftoverOutcome(
                LeftoverOutcome(
                    reviewDate = current.reviewDate,
                    choice = choice,
                    target = current.target,
                    total = total,
                    moved = if (choice == LeftoverChoice.MOVE_ALL) total else 0
                )
            )
            if (choice == LeftoverChoice.MOVE_ALL) {
                moveTasks(current.tasks.map { it.id }, current.target.dateFrom(LocalDate.now(clock)))
            }
        }
    }

    /**
     * Acción sobre una tarea en el modo "una a una". [otherDay] es el día elegido para
     * [TaskAction.OTHER_DAY].
     */
    fun resolveLeftover(taskId: String, action: TaskAction, otherDay: LocalDate? = null) {
        val current = leftovers.value ?: return
        if (current.tasks.none { it.id == taskId }) return
        val targetDate = current.target.dateFrom(LocalDate.now(clock))
        // Un día anterior al propuesto la dejaría otra vez pendiente (el selector ya no lo deja elegir)
        if (action == TaskAction.OTHER_DAY && (otherDay == null || otherDay.isBefore(targetDate))) return
        viewModelScope.launch {
            preferences.countLeftoverResolved(current.reviewDate, moved = action != TaskAction.DONE)
            when (action) {
                TaskAction.MOVE_TO_TARGET -> moveTasks(listOf(taskId), targetDate)
                TaskAction.OTHER_DAY -> moveTasks(listOf(taskId), otherDay!!)
                TaskAction.DONE -> toggleTaskCompletion(taskId)
            }
        }
    }

    /**
     * Prioridades del día: [ChatReply.FOCUS_CONFIRM] con las tareas marcadas o
     * [ChatReply.FOCUS_SKIP] ("Hoy no").
     */
    fun answerFocus(reply: ChatReply, selectedTaskIds: List<String>) {
        val candidates = focus.value?.candidates ?: return
        val chosen = when (reply) {
            ChatReply.FOCUS_CONFIRM -> candidates.tasks.filter { it.id in selectedTaskIds }
                .ifEmpty { return }
            ChatReply.FOCUS_SKIP -> emptyList()
            else -> return
        }
        viewModelScope.launch { chooseDailyFocus(candidates.date, chosen) }
    }

    /**
     * Día de nómina: [ChatReply.PAYDAY_REGISTER] ("Apuntar nómina"; la pantalla abre además Ahorros)
     * o [ChatReply.PAYDAY_LATER] ("Ahora no").
     */
    fun answerPayday(reply: ChatReply) {
        val current = payday.value?.payday ?: return
        val answer = when (reply) {
            ChatReply.PAYDAY_REGISTER -> PaydayAnswer.REGISTER
            ChatReply.PAYDAY_LATER -> PaydayAnswer.LATER
            else -> return
        }
        viewModelScope.launch { preferences.savePaydayAnswer(current.date, answer) }
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

    /** Primer día que se puede elegir en "Otro día": el propuesto (antes seguiría pendiente). */
    fun firstSelectableDay(): LocalDate =
        (leftovers.value?.target ?: MoveTarget.TOMORROW).dateFrom(LocalDate.now(clock))

    private fun visibleMessageIds(): Set<String>? =
        uiState.value?.conversation?.messages?.map { it.id }?.toSet()

    private companion object {
        const val HISTORY_DAYS = 7L
    }
}
