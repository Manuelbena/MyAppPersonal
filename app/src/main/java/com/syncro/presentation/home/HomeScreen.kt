package com.syncro.presentation.home

import com.syncro.presentation.theme.NotebookFontFamily
import androidx.compose.ui.platform.LocalContext
import com.syncro.presentation.components.rememberCurrentMinute
import com.syncro.presentation.components.shareEvent
import com.syncro.presentation.components.LocalWidthClass
import com.syncro.presentation.components.WidthClass
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.event.EventDetailSheet
import com.syncro.presentation.notes.NoteDetailSheet
import com.syncro.presentation.task.TaskDetailSheet
import com.syncro.presentation.home.components.*
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.navigation.AppScreen
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    // El "+" del widget: abrir la hoja de nueva tarea nada más entrar
    openQuickTask: Boolean = false,
    onQuickTaskOpened: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Hora actual al minuto: mueve la línea "Ahora" y el progreso de los eventos en curso
    val now = rememberCurrentMinute()
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
            viewModel.syncFromGoogle()
        }
    }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
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
            }
        }
    }
    
    // Formatear la fecha de hoy para el header
    val today = LocalDate.now()
    val formattedDate = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() } + ", " + 
        today.dayOfMonth + " de " + 
        today.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))

    val dayTitle = "Agenda del día ${uiState.selectedDate.dayOfMonth}"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddItemSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .padding(bottom = 110.dp) // Flota sobre el degradado
                    .size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
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
                onTodayClick = { viewModel.onDaySelected(LocalDate.now()) }
            )
        }
        val weekStrip: @Composable () -> Unit = {
            WeekCalendarStrip(
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.onDaySelected(it) }
            )
        }
        val summaryCards: @Composable () -> Unit = {
            AssistantCard(
                quote = uiState.quote,
                author = uiState.quoteAuthor
            )

            // Prioridades del día (se eligen en el chat del asistente)
            if (uiState.focusTasks.isNotEmpty()) {
                FocusCard(
                    tasks = uiState.focusTasks,
                    isToday = uiState.selectedDate == LocalDate.now(),
                    onToggle = { viewModel.toggleTaskCompletion(it.id) },
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
                    onAddEventClick = { showAddItemSheet = true }
                )
            }
        }
        val tasksCard: @Composable () -> Unit = {
            if (dayTasks.isNotEmpty()) {
                TasksCard(
                    tasks = dayTasks,
                    onToggle = { viewModel.toggleTaskCompletion(it.id) },
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
                            viewModel.toggleSubtaskCompletion(event.id, subtaskTitle)
                        },
                        onToggleEvent = {
                            viewModel.toggleEventCompletion(event.id)
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
                    onRefresh = { viewModel.syncFromGoogle() },
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
                        onRefresh = { viewModel.syncFromGoogle() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // El contenido principal siempre es scrollable para que el Assistant pueda subir
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
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
                viewModel.saveNote(id, title, content, color)
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
                    viewModel.deleteNote(note)
                }
            )
        }

    if (showDetailedEventSheet || selectedEventForEdit != null) {
        AddEventScreen(
            eventToEdit = selectedEventForEdit,
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
                viewModel.saveQuickTask(title, description, date)
                showQuickTaskSheet = false
            },
            // Se crea en el día que se está mirando (hoy, si se mira un día pasado)
            initialDate = maxOf(uiState.selectedDate, LocalDate.now())
        )
    }

    selectedEventIdForDetail
        ?.let { id -> uiState.timelineItems.firstOrNull { it is SyncroItem.Event && it.id == id } as SyncroItem.Event? }
        ?.let { event ->
            EventDetailSheet(
                event = event,
                onDismiss = { selectedEventIdForDetail = null },
                onToggleCompleted = { viewModel.toggleEventCompletion(event.id) },
                onSubtaskToggle = { viewModel.toggleSubtaskCompletion(event.id, it) },
                onEdit = {
                    selectedEventIdForDetail = null
                    selectedEventForEdit = event
                },
                onShare = { context.shareEvent(event) },
                onDelete = {
                    selectedEventIdForDetail = null
                    viewModel.deleteEvent(event.id)
                }
            )
        }

    selectedTaskIdForDetail
        ?.let { id -> uiState.timelineItems.firstOrNull { it is SyncroItem.Task && it.id == id } as SyncroItem.Task? }
        ?.let { task ->
            TaskDetailSheet(
                task = task,
                onDismiss = { selectedTaskIdForDetail = null },
                onToggleCompleted = { viewModel.toggleTaskCompletion(task.id) },
                onDelete = {
                    selectedTaskIdForDetail = null
                    viewModel.deleteTask(task.id)
                }
            )
        }
}

/**
 * Degradados arriba y abajo de una zona con scroll: arriba el contenido se desvanece al subir y
 * abajo se ve por detrás de la barra de navegación flotante.
 */
@Composable
private fun BoxScope.EdgeFades() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .align(Alignment.TopCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        Color.Transparent
                    )
                )
            )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .align(Alignment.BottomCenter)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )
}
