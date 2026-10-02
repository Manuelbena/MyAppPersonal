package com.syncro.presentation.calendar

import androidx.compose.ui.platform.LocalContext
import com.syncro.presentation.components.rememberCurrentMinute
import com.syncro.presentation.components.shareEvent
import com.syncro.presentation.components.AddFab
import com.syncro.presentation.components.LocalWidthClass
import com.syncro.presentation.components.WidthClass
import com.syncro.presentation.event.EventDetailSheet
import com.syncro.presentation.task.TaskDetailSheet
import com.syncro.presentation.theme.toColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.syncro.presentation.home.components.AddNoteSheet
import com.syncro.presentation.home.components.EventCard
import com.syncro.presentation.home.components.TaskRow
import com.syncro.presentation.event.AddEventScreen
import com.syncro.presentation.home.components.QuickTaskSheet
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.*

/** Alto de la fila con los nombres de los días (MonthHeader). */
private val MONTH_HEADER_HEIGHT = 36.dp

/** Hueco abajo para la barra de navegación flotante. */
private val BOTTOM_NAV_SPACE = 120.dp

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
    var selectedEventForEdit by remember { mutableStateOf<com.syncro.domain.model.SyncroItem.Event?>(null) }
    var showQuickTaskSheet by remember { mutableStateOf(false) }
    var showAddNoteSheet by remember { mutableStateOf(false) }

    val widthClass = LocalWidthClass.current
    val isExpanded = widthClass == WidthClass.Expanded
    val panelDate = uiState.selectedDate ?: LocalDate.now()

    LaunchedEffect(state.firstVisibleMonth) {
        viewModel.onMonthChanged(state.firstVisibleMonth.yearMonth)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = { AddFab(onClick = { showAddItemSheet = true }) },
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
                },
                onTodayClick = {
                    coroutineScope.launch { state.animateScrollToMonth(YearMonth.now()) }
                }
            )

            if (uiState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            Row(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // En el móvil las casillas son altas y estrechas (proporción fija); en tablet esa
                    // proporción haría el mes enorme, así que las 6 filas se reparten la altura
                    // disponible, dejando sitio a la cabecera de días y a la barra flotante
                    val cellHeight = if (widthClass == WidthClass.Compact) null
                    else ((maxHeight - MONTH_HEADER_HEIGHT - BOTTOM_NAV_SPACE) / 6).coerceAtLeast(72.dp)
                    HorizontalCalendar(
                        state = state,
                        modifier = Modifier.fillMaxSize(),
                        dayContent = { day ->
                            Day(
                                day = day,
                                items = uiState.events[day.date] ?: emptyList(),
                                onClick = { viewModel.onDateSelected(day.date) },
                                cellHeight = cellHeight,
                                isSelected = isExpanded && day.date == panelDate
                            )
                        },
                        monthHeader = { _ ->
                            MonthHeader(daysOfWeek = daysOfWeek)
                        }
                    )
                }

                // Tablet en horizontal: el detalle del día seleccionado (hoy, si no hay ninguno)
                // queda fijo a la derecha en vez de abrirse en un diálogo
                if (isExpanded) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = BOTTOM_NAV_SPACE)
                    ) {
                        DayDetailsContent(
                            date = panelDate,
                            items = uiState.events[panelDate] ?: emptyList(),
                            onClose = null,
                            onEditEvent = { selectedEventForEdit = it },
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal de detalle del día (en tablet horizontal ya está en el panel lateral)
    uiState.selectedDate?.takeUnless { isExpanded }?.let { date ->
        DayDetailsDialog(
            date = date,
            items = uiState.events[date] ?: emptyList(),
            onDismiss = { viewModel.onDateSelected(null) },
            onEditEvent = {
                viewModel.onDateSelected(null)
                selectedEventForEdit = it
            },
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
            },
            onNoteClick = {
                showAddItemSheet = false
                showAddNoteSheet = true
            }
        )
    }

    if (showAddNoteSheet) {
        // En CalendarScreen de momento no manejamos guardado de notas, 
        // pero mostramos el modal para consistencia UI
        AddNoteSheet(
            onDismiss = { showAddNoteSheet = false },
            onSave = { _, _, _, _ -> showAddNoteSheet = false }
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
                viewModel.saveTask(title, description, date)
                showQuickTaskSheet = false
            },
            // Se crea en el día seleccionado del calendario (hoy, si no hay ninguno o ya pasó)
            initialDate = maxOf(uiState.selectedDate ?: LocalDate.now(), LocalDate.now())
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailsDialog(
    date: LocalDate,
    items: List<SyncroItem>,
    onDismiss: () -> Unit,
    onEditEvent: (SyncroItem.Event) -> Unit,
    viewModel: CalendarViewModel
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        // En tablet vertical no se estira de lado a lado
        modifier = Modifier
            .padding(24.dp)
            .widthIn(max = 560.dp),
        content = {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                DayDetailsContent(
                    date = date,
                    items = items,
                    onClose = onDismiss,
                    onEditEvent = onEditEvent,
                    viewModel = viewModel,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                )
            }
        }
    )
}

/**
 * Lo que hay un día: en el móvil dentro de un diálogo y en tablet horizontal en el panel lateral
 * (sin botón de cerrar, [onClose] null). Tocar un elemento abre su hoja de detalle.
 */
@Composable
fun DayDetailsContent(
    date: LocalDate,
    items: List<SyncroItem>,
    onClose: (() -> Unit)?,
    onEditEvent: (SyncroItem.Event) -> Unit,
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val now = rememberCurrentMinute()
    // Se guarda el id y no el elemento para que el detalle refleje los cambios (subtareas, completar)
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var selectedEventId by remember { mutableStateOf<String?>(null) }

    selectedTaskId
        ?.let { id -> items.firstOrNull { it is SyncroItem.Task && it.id == id } as SyncroItem.Task? }
        ?.let { task ->
            TaskDetailSheet(
                task = task,
                onDismiss = { selectedTaskId = null },
                onToggleCompleted = { viewModel.toggleTaskCompletion(task.id) },
                onDelete = {
                    selectedTaskId = null
                    viewModel.deleteTask(task.id)
                }
            )
        }

    selectedEventId
        ?.let { id -> items.firstOrNull { it is SyncroItem.Event && it.id == id } as SyncroItem.Event? }
        ?.let { event ->
            EventDetailSheet(
                event = event,
                onDismiss = { selectedEventId = null },
                onToggleCompleted = { viewModel.toggleEventCompletion(event.id) },
                onSubtaskToggle = { viewModel.toggleSubtaskCompletion(event.id, it) },
                onEdit = {
                    selectedEventId = null
                    onEditEvent(event)
                },
                onShare = { context.shareEvent(event) },
                onDelete = {
                    selectedEventId = null
                    viewModel.deleteEvent(event.id)
                }
            )
        }

    Column(modifier = modifier) {
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

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay eventos ni tareas para este día",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                items.forEach { item ->
                    when (item) {
                        is SyncroItem.Event -> {
                            EventCard(
                                event = item,
                                onSubtaskToggle = { subtaskTitle ->
                                    viewModel.toggleSubtaskCompletion(item.id, subtaskTitle)
                                },
                                onToggleEvent = {
                                    viewModel.toggleEventCompletion(item.id)
                                },
                                onClick = { selectedEventId = item.id },
                                now = now
                            )
                        }
                        is SyncroItem.Task -> {
                            TaskRow(
                                task = item,
                                onToggle = { viewModel.toggleTaskCompletion(item.id) },
                                onClick = { selectedTaskId = item.id }
                            )
                        }
                        is SyncroItem.Note -> {}
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit
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
            IconButton(onClick = onTodayClick) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = "Ir al mes actual")
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
fun Day(
    day: CalendarDay,
    items: List<SyncroItem>,
    onClick: () -> Unit,
    // En tablet la casilla tiene una altura fija (las filas se reparten la pantalla); null = móvil
    cellHeight: Dp? = null,
    // El día que se ve en el panel lateral (tablet horizontal)
    isSelected: Boolean = false
) {
    val large = cellHeight != null
    // Cuántas etiquetas caben: en el móvil 4; en tablet, las que quepan bajo el número
    val maxItems = cellHeight?.let { ((it - 36.dp) / 20.dp).toInt().coerceAtLeast(1) } ?: 4
    val isToday = day.date == LocalDate.now()
    
    // Obtener el color del primer evento o tarea para el fondo
    val dayColor = remember(items) {
        items.firstOrNull { it is SyncroItem.Event || it is SyncroItem.Task }?.let { item ->
            when (item) {
                is SyncroItem.Event -> item.categoryColor.toColor()
                is SyncroItem.Task -> item.categoryColor?.toColor() ?: Color(0xFF64748B) // Slate500 por defecto
                else -> null
            }
        }
    }

    // Un evento de todo el día marca la casilla entera con un borde de su color: se ve de un vistazo
    val allDayEventColor = remember(items) {
        items.filterIsInstance<SyncroItem.Event>().firstOrNull { it.isAllDay }?.categoryColor?.toColor()
    }

    Box(
        modifier = Modifier
            .then(if (cellHeight != null) Modifier.height(cellHeight) else Modifier.aspectRatio(0.6f))
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    dayColor != null -> dayColor.copy(alpha = 0.15f)
                    isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else if (allDayEventColor != null) Modifier.border(2.dp, allDayEventColor, RoundedCornerShape(8.dp))
                else Modifier
            )
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

            // Items (los que quepan)
            val visibleItems = items.take(maxItems)
            visibleItems.forEach { item ->
                when (item) {
                    is SyncroItem.Event -> EventSnippet(item, large)
                    is SyncroItem.Task -> TaskSnippet(item, large)
                    is SyncroItem.Note -> {}
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (items.size > maxItems) {
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
fun EventSnippet(event: SyncroItem.Event, large: Boolean = false) {
    ItemSnippet(title = event.title, color = event.categoryColor.toColor(), large = large)
}

/**
 * Las tareas tienen su propio aspecto (círculo de check delante), tengan hora o no, para que no se
 * confundan con los eventos.
 */
@Composable
fun TaskSnippet(task: SyncroItem.Task, large: Boolean = false) {
    val color = task.categoryColor?.toColor() ?: MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (task.isCompleted) "Tarea completada" else "Tarea",
                tint = color,
                modifier = Modifier.size(if (large) 12.dp else 9.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = task.title,
                // En tablet las casillas son más anchas: texto legible en vez de 8 sp
                fontSize = if (large) 11.sp else 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                lineHeight = if (large) 14.sp else 10.sp
            )
        }
    }
}

/**
 * Etiqueta de un evento dentro de la celda del día: fondo suave y una marca de color. Los de todo
 * el día se señalan con el borde de la casilla entera (ver [Day]).
 */
@Composable
private fun ItemSnippet(title: String, color: Color, large: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp, if (large) 16.dp else 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                // En tablet las casillas son más anchas: texto legible en vez de 8 sp
                fontSize = if (large) 11.sp else 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = if (large) 14.sp else 10.sp
            )
        }
    }
}
