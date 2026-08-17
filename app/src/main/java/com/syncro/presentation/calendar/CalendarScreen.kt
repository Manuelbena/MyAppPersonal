package com.syncro.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.home.components.AddItemBottomSheet
import com.syncro.presentation.home.components.EventCard
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.home.components.QuickTaskSheet
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(100) }
    val endMonth = remember { currentMonth.plusMonths(100) }
    val daysOfWeek = remember { daysOfWeek(firstDayOfWeek = DayOfWeek.MONDAY) }
    
    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = daysOfWeek.first()
    )

    val coroutineScope = rememberCoroutineScope()
    
    // Estados para las hojas modales
    var showAddItemSheet by remember { mutableStateOf(false) }
    var showDetailedEventSheet by remember { mutableStateOf(false) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.firstVisibleMonth) {
        viewModel.onMonthChanged(state.firstVisibleMonth.yearMonth)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddItemSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .padding(bottom = 110.dp)
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
            Spacer(modifier = Modifier.height(16.dp)) // Espacio por encima

            // Header
            CalendarHeader(
                selectedMonth = state.firstVisibleMonth.yearMonth,
                onPreviousMonth = {
                    val prev = state.firstVisibleMonth.yearMonth.minusMonths(1)
                    coroutineScope.launch { state.animateScrollToMonth(prev) }
                },
                onNextMonth = {
                    val next = state.firstVisibleMonth.yearMonth.plusMonths(1)
                    coroutineScope.launch { state.animateScrollToMonth(next) }
                }
            )

            // Contenedor con degradados
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalCalendar(
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    dayContent = { day ->
                        Day(
                            day = day,
                            events = uiState.events[day.date] ?: emptyList(),
                            onClick = { viewModel.onDateSelected(day.date) }
                        )
                    },
                    monthHeader = { _ ->
                        MonthHeader(daysOfWeek = daysOfWeek)
                    }
                )

                // Degradados
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(MaterialTheme.colorScheme.background, Color.Transparent)
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
                                listOf(
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

    // Modal de detalle del día
    uiState.selectedDate?.let { date ->
        DayDetailsDialog(
            date = date,
            events = uiState.events[date] ?: emptyList(),
            onDismiss = { viewModel.onDateSelected(null) },
            viewModel = viewModel
        )
    }

    // Modales de creación (Misma funcionalidad que en Home)
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
            }
        )
    }

    if (showDetailedEventSheet) {
        AddEventScreen(
            onDismiss = { showDetailedEventSheet = false }
        )
    }

    if (showQuickTaskSheet) {
        QuickTaskSheet(
            onDismiss = { showQuickTaskSheet = false },
            onSave = { _, _, _, _, _, _ -> 
                // En Calendar ViewModel no tenemos implementado save aún, 
                // pero conectamos la UI
                showQuickTaskSheet = false 
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsDialog(
    date: LocalDate,
    events: List<SyncroItem.Event>,
    onDismiss: () -> Unit,
    viewModel: CalendarViewModel
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = date.format(java.time.format.DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-ES"))),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), 
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (events.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No hay eventos para este día",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 8.dp)
                        ) {
                            events.forEach { event ->
                                EventCard(
                                    event = event,
                                    onSubtaskToggle = { subtaskTitle ->
                                        viewModel.toggleSubtaskCompletion(event.id, subtaskTitle)
                                    },
                                    onToggleEvent = {
                                        viewModel.toggleEventCompletion(event.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CalendarHeader(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                text = selectedMonth.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es-ES")).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
        }

        Row {
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
            }
        }
    }
}

@Composable
fun MonthHeader(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es-ES")).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (dayOfWeek == DayOfWeek.SUNDAY) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Day(day: CalendarDay, events: List<SyncroItem.Event>, onClick: () -> Unit) {
    val isToday = day.date == LocalDate.now()
    
    Box(
        modifier = Modifier
            .aspectRatio(0.6f) // Más alto para que quepan eventos (era 0.7f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (day.position != DayPosition.MonthDate) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                } else if (day.date.dayOfWeek == DayOfWeek.SUNDAY) {
                    Color.Red
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Eventos (máximo 4 ahora que es más alto)
            val visibleEvents = events.take(4)
            visibleEvents.forEach { event ->
                EventSnippet(event)
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (events.size > 4) {
                Text(
                    text = "...",
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EventSnippet(event: SyncroItem.Event) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(event.categoryColor.copy(alpha = 0.2f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(event.categoryColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = event.title,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 10.sp
            )
        }
    }
}
