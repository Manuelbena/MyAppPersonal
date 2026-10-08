package com.syncro.presentation.home

import com.syncro.presentation.theme.NotebookFontFamily
import androidx.compose.ui.platform.LocalContext
import com.syncro.presentation.components.rememberCurrentMinute
import com.syncro.presentation.components.shareEvent
import com.syncro.presentation.components.AddFab
import com.syncro.presentation.components.EdgeFades
import com.syncro.presentation.components.LocalWidthClass
import com.syncro.presentation.components.WidthClass
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.event.EventDetailSheet
import com.syncro.presentation.notes.NoteDetailSheet
import com.syncro.presentation.task.TaskDetailSheet
import com.syncro.presentation.home.components.*
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.nextUp
import kotlinx.coroutines.launch
import com.syncro.presentation.navigation.AppScreen
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    // El "+" del widget: abrir la hoja de nueva tarea nada más entrar
    openQuickTask: Boolean = false,
    onQuickTaskOpened: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    HomeScreenContent(
        uiState = uiState,
        effectFlow = viewModel.effect,
        onOpenSettings = onOpenSettings,
        openQuickTask = openQuickTask,
        onQuickTaskOpened = onQuickTaskOpened,
        onNavigateToNotes = onNavigateToNotes,
        onNavigateToSavings = onNavigateToSavings,
        onRefreshToday = viewModel::refreshToday,
        onSyncFromGoogle = { viewModel.syncFromGoogle() },
        onUndo = viewModel::undo,
        onUndoExpired = viewModel::undoExpired,
        onGoToToday = viewModel::goToToday,
        onDaySelected = viewModel::onDaySelected,
        onHideDailyQuote = viewModel::hideDailyQuote,
        onToggleTaskCompletion = viewModel::toggleTaskCompletion,
        onToggleSubtaskCompletion = viewModel::toggleSubtaskCompletion,
        onToggleEventCompletion = viewModel::toggleEventCompletion,
        onSaveNote = viewModel::saveNote,
        onDeleteNote = viewModel::deleteNote,
        onSaveQuickTask = viewModel::saveQuickTask,
        onDeleteEvent = viewModel::deleteEvent,
        onDeleteEventAndFollowing = viewModel::deleteEventAndFollowing,
        onDeleteTask = viewModel::deleteTask,
        onDeleteTaskAndFollowing = viewModel::deleteTaskAndFollowing
    )
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    effectFlow: kotlinx.coroutines.flow.Flow<HomeEffect>,
    onOpenSettings: () -> Unit,
    openQuickTask: Boolean = false,
    onQuickTaskOpened: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToSavings: () -> Unit = {},
    onRefreshToday: () -> Unit,
    onSyncFromGoogle: () -> Unit,
    onUndo: (HomeUndo) -> Unit,
    onUndoExpired: (HomeUndo) -> Unit,
    onGoToToday: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    onHideDailyQuote: () -> Unit,
    onToggleTaskCompletion: (String) -> Unit,
    onToggleSubtaskCompletion: (String, String) -> Unit,
    onToggleEventCompletion: (String) -> Unit,
    onSaveNote: (String?, String, String, Color) -> Unit,
    onDeleteNote: (SyncroItem.Note) -> Unit,
    onSaveQuickTask: (String, String, LocalDate) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onDeleteEventAndFollowing: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onDeleteTaskAndFollowing: (String) -> Unit
) {
    // La última frase mostrada: así la tarjeta no se queda vacía mientras se recoge al cerrarla
    var shownQuote by remember { mutableStateOf(uiState.quote) }
    uiState.quote?.let { shownQuote = it }
    // Igual con el aviso de sincronización: se recoge con su último texto
    var shownSyncNotice by remember { mutableStateOf(uiState.syncNotice) }
    uiState.syncNotice?.let { shownSyncNotice = it }
    // Hora actual al minuto: mueve la línea "Ahora" y el progreso de los eventos en curso
    val now = rememberCurrentMinute()
    // Si la app sigue abierta al pasar la medianoche (o vuelve a primer plano otro día), "hoy" avanza
    LaunchedEffect(now.toLocalDate()) { onRefreshToday() }
    LifecycleResumeEffect(Unit) {
        onRefreshToday()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    // En tablet horizontal, la columna del resumen tiene su propio scroll
    val summaryScrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddItemSheet by remember { mutableStateOf(false) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }
    LaunchedEffect(openQuickTask) {
        if (openQuickTask) {
            showQuickTaskSheet = true
            onQuickTaskOpened()
        }
    }
    var showDetailedEventSheet by remember { mutableStateOf(false) }
    var selectedEventForEdit by remember { mutableStateOf<com.syncro.domain.model.SyncroItem.Event?>(null) }
    var showAddNoteSheet by remember { mutableStateOf(false) }
    // Se guarda el id y no el elemento para que el detalle refleje los cambios (subtareas, completar)
    var selectedTaskIdForDetail by remember { mutableStateOf<String?>(null) }
    var selectedEventIdForDetail by remember { mutableStateOf<String?>(null) }
    var selectedNoteIdForDetail by remember { mutableStateOf<String?>(null) }
    var noteToEdit by remember { mutableStateOf<SyncroItem.Note?>(null) }

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Reintentar sincronización si el usuario autorizó
            onSyncFromGoogle()
        }
    }

    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            when (effect) {
                is HomeEffect.LaunchAuthRecovery -> {
                    authLauncher.launch(effect.intent)
                }
                is HomeEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        duration = SnackbarDuration.Short
                    )
                }
                // Aparte, para no frenar los demás avisos mientras se espera al "Deshacer"
                is HomeEffect.OfferUndo -> launch {
                    var undone = false
                    try {
                        val result = snackbarHostState.showSnackbar(
                            message = effect.message,
                            actionLabel = "Deshacer",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            undone = true
                            onUndo(effect.undo)
                        }
                    } finally {
                        // También si se sale de Inicio con el aviso en pantalla: el borrado se sube
                        if (!undone) onUndoExpired(effect.undo)
                    }
                }
            }
        }
    }
    
    // La fecha de hoy para la cabecera y el título de la agenda del día que se mira
    val today = uiState.today
    val formattedDate = fullDayName(today, today)
    val dayTitle = dayTitle(uiState.selectedDate, today)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = { AddFab(onClick = { showAddItemSheet = true }) },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        val widthClass = LocalWidthClass.current
        // Las tareas solo tienen día: van agrupadas en su tarjeta, encima de los eventos
        val dayTasks = uiState.timelineItems.filterIsInstance<SyncroItem.Task>()
        val dayEvents = uiState.timelineItems.filterIsInstance<SyncroItem.Event>()
        val isDayEmpty = !uiState.isLoading && dayTasks.isEmpty() && dayEvents.isEmpty()

        // Piezas de la pantalla: en el móvil van en una columna; en tablet horizontal, en dos
        val header: @Composable () -> Unit = {
            HomeHeader(
                userName = uiState.userName,
                currentDate = formattedDate,
                userPhotoUrl = uiState.userPhotoUrl,
                onOpenSettings = onOpenSettings,
                onTodayClick = onGoToToday,
                // Mirando hoy, el botón no haría nada: solo sale al ver otro día
                showTodayButton = uiState.selectedDate != today
            )
            // Sin conexión, sync fallida o cambios sin subir: se avisa bajo la cabecera
            AnimatedVisibility(
                visible = uiState.syncNotice != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                shownSyncNotice?.let { SyncNoticeBar(notice = it, onRetry = onSyncFromGoogle) }
            }
        }
        val weekStrip: @Composable () -> Unit = {
            WeekCalendarStrip(
                today = uiState.today,
                selectedDate = uiState.selectedDate,
                onDateSelected = onDaySelected,
                dayMarks = uiState.dayMarks
            )
        }
        // Deslizar el contenido a los lados cambia de día
        val daySwipe = Modifier.swipeBetweenDays(
            onPreviousDay = { onDaySelected(uiState.selectedDate.minusDays(1)) },
            onNextDay = { onDaySelected(uiState.selectedDate.plusDays(1)) }
        )
        val summaryCards: @Composable () -> Unit = {
            // Lo próximo de hoy (evento en curso, el siguiente y las tareas); solo mirando hoy
            if (uiState.selectedDate == today) {
                nextUp(uiState.timelineItems, now)?.let { next ->
                    NextUpCard(
                        nextUp = next,
                        now = now,
                        onEventClick = { selectedEventIdForDetail = it.id }
                    )
                }
            }

            // La frase del día, si toca; al cerrarla se recoge en vez de desaparecer de golpe
            AnimatedVisibility(
                visible = uiState.quote != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                shownQuote?.let { DailyQuoteCard(quote = it.text, author = it.author, onClose = onHideDailyQuote) }
            }

            // Ahorros del mes, si se activó en Ajustes > Asistente (es del mes: solo mirando hoy)
            if (uiState.selectedDate == today) {
                uiState.savings?.let { HomeSavingsCard(savings = it, onOpenSavings = onNavigateToSavings) }
            }

            // Prioridades del día (se eligen en el chat del asistente)
            if (uiState.focusTasks.isNotEmpty()) {
                FocusCard(
                    tasks = uiState.focusTasks,
                    isToday = uiState.selectedDate == uiState.today,
                    onToggle = { onToggleTaskCompletion(it.id) },
                    onClick = { selectedTaskIdForDetail = it.id }
                )
            }
        }
        val dayHeading: @Composable () -> Unit = {
            Text(
                text = dayTitle,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = NotebookFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
        val emptyDay: @Composable () -> Unit = {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                EmptyStateView(
                    date = uiState.selectedDate,
                    today = today,
                    onAddTask = { showQuickTaskSheet = true },
                    onAddEvent = { showDetailedEventSheet = true }
                )
            }
        }
        val tasksCard: @Composable () -> Unit = {
            if (dayTasks.isNotEmpty()) {
                TasksCard(
                    tasks = dayTasks,
                    onToggle = { onToggleTaskCompletion(it.id) },
                    onClick = { selectedTaskIdForDetail = it.id }
                )
            }
        }
        // Timeline de eventos
        val eventsTimeline: @Composable () -> Unit = {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                val nowIndex = nowIndicatorIndex(dayEvents, uiState.selectedDate, now)
                dayEvents.forEachIndexed { index, event ->
                    if (index == nowIndex) NowIndicator(now)
                    EventCard(
                        event = event,
                        onSubtaskToggle = { subtaskTitle ->
                            onToggleSubtaskCompletion(event.id, subtaskTitle)
                        },
                        onToggleEvent = {
                            onToggleEventCompletion(event.id)
                        },
                        onClick = { selectedEventIdForDetail = event.id },
                        now = now
                    )
                }
                // Si ya empezaron todos los eventos del día, la línea "Ahora" va al final
                if (nowIndex == dayEvents.size) NowIndicator(now)
            }
        }
        val notesSection: @Composable () -> Unit = {
            if (uiState.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                NotesSection(
                    notes = uiState.notes,
                    onNoteClick = { selectedNoteIdForDetail = it.id },
                    onSeeAllClick = onNavigateToNotes
                )
            }
        }

        if (widthClass == WidthClass.Expanded) {
            // Tablet en horizontal: a la izquierda el resumen del día (semana, frase, prioridades,
            // tareas y notas) y a la derecha la agenda de eventos, cada una con su propio scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                header()
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = onSyncFromGoogle,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(summaryScrollState)
                            ) {
                                weekStrip()
                                Spacer(modifier = Modifier.height(16.dp))
                                summaryCards()
                                tasksCard()
                                notesSection()
                                Spacer(modifier = Modifier.height(160.dp)) // Espacio para el degradado y menú
                            }
                            EdgeFades()
                        }
                        Box(
                            modifier = Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(daySwipe)
                                    .verticalScroll(scrollState)
                            ) {
                                dayHeading()
                                when {
                                    isDayEmpty -> emptyDay()
                                    dayEvents.isEmpty() && !uiState.isLoading -> Text(
                                        "No hay eventos este día",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    else -> eventsTimeline()
                                }
                                Spacer(modifier = Modifier.height(160.dp))
                            }
                            EdgeFades()
                        }
                    }
                }
            }
        } else {
            // Móvil, o tablet en vertical con la columna centrada para no estirar las tarjetas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (widthClass == WidthClass.Medium) 720.dp else Dp.Unspecified)
                        .fillMaxSize()
                ) {
                    // PARTE FIJA: Cabecera y Calendario
                    header()
                    weekStrip()

                    // CONTENEDOR CON DEGRADADOS (Arriba y Abajo)
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = onSyncFromGoogle,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // El contenido principal siempre es scrollable para que el Assistant pueda subir
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(daySwipe)
                                    .verticalScroll(scrollState)
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                summaryCards()
                                dayHeading()
                                if (isDayEmpty) emptyDay()
                                tasksCard()
                                if (dayTasks.isNotEmpty()) Spacer(modifier = Modifier.height(8.dp))
                                eventsTimeline()
                                notesSection()
                                Spacer(modifier = Modifier.height(160.dp)) // Espacio extra para el degradado y menú
                            }
                            EdgeFades()
                        }
                    }
                }
            }
        }
    }

    if (showAddItemSheet) {
        AddItemBottomSheet(
            onDismiss = { showAddItemSheet = false },
            onQuickTaskClick = { 
                showAddItemSheet = false
                showQuickTaskSheet = true
            },
            onDetailedEventClick = { 
                showAddItemSheet = false
                showDetailedEventSheet = true
            },
            onNoteClick = {
                showAddItemSheet = false
                showAddNoteSheet = true
            }
        )
    }

    if (showAddNoteSheet) {
        AddNoteSheet(
            initialNote = noteToEdit,
            onDismiss = {
                showAddNoteSheet = false
                noteToEdit = null
            },
            onSave = { id, title, content, color ->
                onSaveNote(id, title, content, color)
                showAddNoteSheet = false
                noteToEdit = null
            }
        )
    }

    selectedNoteIdForDetail
        ?.let { id -> uiState.notes.firstOrNull { it.id == id } }
        ?.let { note ->
            NoteDetailSheet(
                note = note,
                onDismiss = { selectedNoteIdForDetail = null },
                onEdit = {
                    selectedNoteIdForDetail = null
                    noteToEdit = note
                    showAddNoteSheet = true
                },
                onDelete = {
                    selectedNoteIdForDetail = null
                    onDeleteNote(note)
                }
            )
        }

    if (showDetailedEventSheet || selectedEventForEdit != null) {
        AddEventScreen(
            eventToEdit = selectedEventForEdit,
            // Como las tareas: en el día que se mira (hoy, si se mira un día pasado)
            initialDate = maxOf(uiState.selectedDate, uiState.today),
            onDismiss = { 
                showDetailedEventSheet = false 
                selectedEventForEdit = null
            }
        )
    }

    if (showQuickTaskSheet) {
        QuickTaskSheet(
            onDismiss = { showQuickTaskSheet = false },
            onSave = { title, description, date ->
                onSaveQuickTask(title, description, date)
                showQuickTaskSheet = false
            },
            // Se crea en el día que se está mirando (hoy, si se mira un día pasado)
            initialDate = maxOf(uiState.selectedDate, uiState.today),
            today = uiState.today
        )
    }

    selectedEventIdForDetail
        ?.let { id -> uiState.timelineItems.firstOrNull { it is SyncroItem.Event && it.id == id } as SyncroItem.Event? }
        ?.let { event ->
            EventDetailSheet(
                event = event,
                onDismiss = { selectedEventIdForDetail = null },
                onToggleCompleted = { onToggleEventCompletion(event.id) },
                onSubtaskToggle = { onToggleSubtaskCompletion(event.id, it) },
                onEdit = {
                    selectedEventIdForDetail = null
                    selectedEventForEdit = event
                },
                onShare = { context.shareEvent(event) },
                onDelete = {
                    selectedEventIdForDetail = null
                    onDeleteEvent(event.id)
                },
                onDeleteFollowing = {
                    selectedEventIdForDetail = null
                    onDeleteEventAndFollowing(event.id)
                }
            )
        }

    selectedTaskIdForDetail
        ?.let { id -> uiState.timelineItems.firstOrNull { it is SyncroItem.Task && it.id == id } as SyncroItem.Task? }
        ?.let { task ->
            TaskDetailSheet(
                task = task,
                onDismiss = { selectedTaskIdForDetail = null },
                onToggleCompleted = { onToggleTaskCompletion(task.id) },
                onDelete = {
                    selectedTaskIdForDetail = null
                    onDeleteTask(task.id)
                },
                onDeleteFollowing = {
                    selectedTaskIdForDetail = null
                    onDeleteTaskAndFollowing(task.id)
                }
            )
        }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun HomeScreenPreview() {
    com.syncro.presentation.theme.SyncroTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                userName = "Usuario",
                today = LocalDate.now(),
                selectedDate = LocalDate.now(),
                timelineItems = listOf(
                    com.syncro.domain.model.SyncroItem.Task(
                        id = "1",
                        title = "Tarea de prueba",
                        description = "Esta es una tarea",
                        date = LocalDate.now(),
                        time = java.time.LocalTime.MIDNIGHT,
                        isCompleted = false
                    )
                )
            ),
            effectFlow = kotlinx.coroutines.flow.emptyFlow(),
            onOpenSettings = {},
            openQuickTask = false,
            onQuickTaskOpened = {},
            onNavigateToNotes = {},
            onNavigateToSavings = {},
            onRefreshToday = {},
            onSyncFromGoogle = {},
            onUndo = {},
            onUndoExpired = {},
            onGoToToday = {},
            onDaySelected = {},
            onHideDailyQuote = {},
            onToggleTaskCompletion = {},
            onToggleSubtaskCompletion = { _, _ -> },
            onToggleEventCompletion = {},
            onSaveNote = { _, _, _, _ -> },
            onDeleteNote = {},
            onSaveQuickTask = { _, _, _ -> },
            onDeleteEvent = {},
            onDeleteEventAndFollowing = {},
            onDeleteTask = {},
            onDeleteTaskAndFollowing = {}
        )
    }
}
