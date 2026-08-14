package com.syncro.presentation.home

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    onNavigateToAddEvent: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    var showAddItemSheet by remember { mutableStateOf(false) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }
    var selectedTaskForDetail by remember { mutableStateOf<SyncroItem.Task?>(null) }

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
            }
        }
    }
    
    // Formatear la fecha actual para el header
    val formattedDate = uiState.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() } + ", " + 
        uiState.selectedDate.dayOfMonth + " de " + 
        uiState.selectedDate.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                onSettingsClick = { /* TODO */ },
                onThemeToggle = onThemeToggle,
                isDarkTheme = isDarkTheme,
                onTodayClick = { viewModel.onDaySelected(LocalDate.now()) }
            )

            WeekCalendarStrip(
                selectedDate = uiState.selectedDate,
                onDateSelected = { viewModel.onDaySelected(it) }
            )

            // CONTENEDOR CON DEGRADADOS (Arriba y Abajo)
            Box(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading && uiState.timelineItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // PARTE SCROLLABLE: Asistente y Timeline
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
                        
                        Spacer(modifier = Modifier.height(16.dp))

                        // Timeline de items (Eventos y Tareas)
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 160.dp) // Espacio extra para el degradado y menú
                        ) {
                            if (uiState.timelineItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No hay eventos para hoy",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            uiState.timelineItems.forEach { item ->
                                when (item) {
                                    is SyncroItem.Event -> EventCard(
                                        event = item,
                                        onSubtaskToggle = { subtaskTitle ->
                                            viewModel.toggleSubtaskCompletion(item.id, subtaskTitle)
                                        }
                                    )
                                    is SyncroItem.Task -> TaskRow(
                                        task = item,
                                        onToggle = { viewModel.toggleTaskCompletion(item.id) },
                                        onClick = { selectedTaskForDetail = item }
                                    )
                                }
                            }
                        }
                    }
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

    if (showAddItemSheet) {
        AddItemBottomSheet(
            onDismiss = { showAddItemSheet = false },
            onQuickTaskClick = { 
                showAddItemSheet = false
                showQuickTaskSheet = true
            },
            onDetailedEventClick = { 
                showAddItemSheet = false
                onNavigateToAddEvent()
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

    selectedTaskForDetail?.let { task ->
        TaskDetailDialog(
            task = task,
            onDismiss = { selectedTaskForDetail = null },
            onComplete = { viewModel.toggleTaskCompletion(task.id) }
        )
    }
}
