package com.syncro.presentation.home

import androidx.compose.ui.platform.LocalContext
import com.syncro.presentation.components.rememberCurrentMinute
import com.syncro.presentation.components.shareEvent
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
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit,
    onNavigateToNotes: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Hora actual al minuto: mueve la línea "Ahora" y el progreso de los eventos en curso
    val now = rememberCurrentMinute()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddItemSheet by remember { mutableStateOf(false) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }
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

    val tasksTitle = "Tareas del día ${uiState.selectedDate.dayOfMonth}"

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // PARTE FIJA: Cabecera y Calendario
            HomeHeader(
                userName = uiState.userName,
                currentDate = formattedDate,
                onThemeToggle = onThemeToggle,
                isDarkTheme = isDarkTheme,
                onTodayClick = { viewModel.onDaySelected(LocalDate.now()) }
            )

            WeekCalendarStrip(
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.onDaySelected(it) }
            )

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

                        AssistantCard(
                            quote = uiState.quote,
                            author = uiState.quoteAuthor
                        )

                        // Timeline de items (Eventos y Tareas)
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = tasksTitle,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )

                            if (!uiState.isLoading && uiState.timelineItems.isEmpty()) {
                                EmptyStateView(
                                    onAddEventClick = { showAddItemSheet = true }
                                )
                            }

                            val nowIndex = nowIndicatorIndex(uiState.timelineItems, uiState.selectedDate, now)
                            uiState.timelineItems.forEachIndexed { index, item ->
                                if (index == nowIndex) NowIndicator(now)
                                when (item) {
                                    is SyncroItem.Event -> EventCard(
                                        event = item,
                                        onSubtaskToggle = { subtaskTitle ->
                                            viewModel.toggleSubtaskCompletion(item.id, subtaskTitle)
                                        },
                                        onToggleEvent = {
                                            viewModel.toggleEventCompletion(item.id)
                                        },
                                        onClick = { selectedEventIdForDetail = item.id },
                                        now = now
                                    )
                                    is SyncroItem.Task -> TaskRow(
                                        task = item,
                                        onToggle = { viewModel.toggleTaskCompletion(item.id) },
                                        onClick = { selectedTaskIdForDetail = item.id }
                                    )
                                    is SyncroItem.Note -> {
                                        // Las notas se muestran en el carrusel, no en el timeline
                                    }
                                }
                            }
                            // Si ya empezó todo lo del día, la línea "Ahora" va al final
                            if (nowIndex == uiState.timelineItems.size) NowIndicator(now)
                        }

                        if (uiState.notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            NotesSection(
                                notes = uiState.notes,
                                onNoteClick = { selectedNoteIdForDetail = it.id },
                                onSeeAllClick = onNavigateToNotes
                            )
                        }

                        Spacer(modifier = Modifier.height(160.dp)) // Espacio extra para el degradado y menú
                    }

                    // Degradado SUPERIOR (Para que las tareas se desvanezcan al subir)
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

                    // Degradado INFERIOR (Para que se vea por detrás del menú)
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
            onSave = { title, description, date, time ->
                viewModel.saveQuickTask(title, description, date, time)
                showQuickTaskSheet = false
            }
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
                onShare = { context.shareEvent(event) }
            )
        }

    selectedTaskIdForDetail
        ?.let { id -> uiState.timelineItems.firstOrNull { it is SyncroItem.Task && it.id == id } as SyncroItem.Task? }
        ?.let { task ->
            TaskDetailSheet(
                task = task,
                onDismiss = { selectedTaskIdForDetail = null },
                onToggleCompleted = { viewModel.toggleTaskCompletion(task.id) }
            )
        }
}
