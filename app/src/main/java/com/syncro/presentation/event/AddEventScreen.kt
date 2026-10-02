package com.syncro.presentation.event

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncro.presentation.components.SheetDragHandle
import com.syncro.presentation.components.GradientSheetInsets
import com.syncro.domain.model.Priority
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.model.isValidEventRange
import com.syncro.presentation.components.DetailCard
import com.syncro.presentation.components.InfoDivider
import com.syncro.presentation.components.SectionTitle
import com.syncro.presentation.components.categoryIcon
import com.syncro.presentation.components.toDisplayTime
import com.syncro.presentation.theme.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    eventToEdit: SyncroItem.Event? = null,
    onDismiss: () -> Unit,
    viewModel: com.syncro.presentation.home.HomeViewModel = hiltViewModel()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // La raya se dibuja dentro de la cabecera para que el degradado empiece en el borde del sheet
        dragHandle = null,
        // Sin el hueco de arriba de Material: lo pone la cabecera, dentro del degradado
        contentWindowInsets = { GradientSheetInsets },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        AddEventContent(
            eventToEdit = eventToEdit,
            onDismiss = onDismiss,
            onSave = { title, desc, loc, date, endDate, start, end, catText, catCol, priority, subs ->
                viewModel.saveDetailedEvent(
                    id = eventToEdit?.id,
                    title = title,
                    description = desc,
                    location = loc,
                    date = date,
                    endDate = endDate,
                    startTime = start,
                    endTime = end,
                    categoryText = catText,
                    categoryColor = catCol,
                    priority = priority,
                    subtasks = subs
                )
                onDismiss()
            }
        )
    }
}

/** Categorías que se pueden elegir; su color es el que se guarda (y el que se envía a Google). */
private val EVENT_CATEGORIES = listOf(
    "Personal" to Emerald500,
    "Trabajo" to Indigo500,
    "Salud" to Color(0xFFFF5252),
    "Ocio" to Amber500,
    "Deporte" to Pink500,
    "Compras" to Violet500,
    "Recados" to Sky500,
    "Otro" to Slate500
).map { (name, color) -> CategoryItem(name, categoryIcon(name), color) }

/** De menor a mayor, como se lee de izquierda a derecha. */
private val PRIORITIES_IN_ORDER = listOf(Priority.LOW, Priority.MEDIUM, Priority.HIGH)

private enum class PickerTarget { StartDate, StartTime, EndDate, EndTime }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventContent(
    eventToEdit: SyncroItem.Event? = null,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, LocalDate, LocalDate, LocalTime, LocalTime, String, Color, Priority?, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var description by remember { mutableStateOf(eventToEdit?.description ?: "") }
    var location by remember { mutableStateOf(eventToEdit?.location ?: "") }
    var selectedCategory by remember { mutableStateOf(eventToEdit?.categoryText ?: "Personal") }
    var selectedPriority by remember { mutableStateOf(eventToEdit?.priority ?: Priority.MEDIUM) }
    var isAllDay by remember { mutableStateOf(eventToEdit?.isAllDay == true) }

    var subtaskInput by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf(*eventToEdit?.subtasks?.map { it.title }?.toTypedArray() ?: arrayOf()) }
    fun addSubtask() {
        if (subtaskInput.isNotBlank()) {
            subtasks.add(subtaskInput.trim())
            subtaskInput = ""
        }
    }

    // Evento nuevo: de la próxima hora en punto a la siguiente (con fechas reales: a las 23:30
    // eso es mañana de 00:00 a 01:00, no un rango imposible dentro de hoy)
    val defaultStart = remember { nextFullHour(LocalDateTime.now()) }
    var startDate by remember { mutableStateOf(eventToEdit?.date ?: defaultStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(eventToEdit?.startTime ?: defaultStart.toLocalTime()) }
    var endDate by remember { mutableStateOf(eventToEdit?.endDate ?: defaultStart.plusHours(1).toLocalDate()) }
    var endTime by remember { mutableStateOf(eventToEdit?.endTime ?: defaultStart.plusHours(1).toLocalTime()) }

    /** Mover el inicio arrastra el fin, conservando la duración (como en Google Calendar). */
    fun moveStart(newStart: LocalDateTime) {
        val newEnd = endAfterMovingStart(startDate.atTime(startTime), endDate.atTime(endTime), newStart)
        startDate = newStart.toLocalDate()
        startTime = newStart.toLocalTime()
        endDate = newEnd.toLocalDate()
        endTime = newEnd.toLocalTime()
    }

    var picker by remember { mutableStateOf<PickerTarget?>(null) }

    // "Todo el día" se guarda como 00:00–00:00: así lo entienden el resto de la app y la sync con Google
    val effectiveStartTime = if (isAllDay) LocalTime.MIDNIGHT else startTime
    val effectiveEndTime = if (isAllDay) LocalTime.MIDNIGHT else endTime
    // Se comparan fecha y hora: 21:30 → 01:00 del día siguiente es válido
    val isTimeRangeValid = isValidEventRange(startDate.atTime(effectiveStartTime), endDate.atTime(effectiveEndTime))
    val canSave = title.isNotBlank() && isTimeRangeValid

    // Una categoría que no está en la lista (p. ej. "General" de Google) se ofrece tal cual, con su
    // color, para que editar el evento no se lo cambie
    val categories = remember(eventToEdit) {
        val own = eventToEdit?.takeIf { e -> EVENT_CATEGORIES.none { it.name == e.categoryText } }
            ?.let { CategoryItem(it.categoryText, categoryIcon(it.categoryText), it.categoryColor.toColor()) }
        listOfNotNull(own) + EVENT_CATEGORIES
    }
    val categoryColor = categories.firstOrNull { it.name == selectedCategory }?.color ?: Emerald500
    val accent by animateColorAsState(categoryColor, label = "eventAccent")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 40.dp)
    ) {
        // Cabecera: degradado del color de la categoría elegida, como en el detalle del evento
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)))
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            SheetDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (eventToEdit != null) "Editar evento" else "Nuevo evento",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onSave(
                            title,
                            description.ifBlank { null },
                            location.ifBlank { null },
                            startDate,
                            endDate,
                            effectiveStartTime,
                            effectiveEndTime,
                            selectedCategory,
                            categoryColor,
                            selectedPriority,
                            subtasks.toList()
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    enabled = canSave,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Guardar", fontWeight = FontWeight.Bold)
                }
            }

            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        "Añade un título",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                singleLine = true,
                colors = transparentTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Cuándo
            DetailCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Rounded.Schedule, accent)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Todo el día",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
                    )
                }
                InfoDivider()
                DateTimeRow(
                    label = "Empieza",
                    date = startDate.toFormDate(),
                    time = startTime.toDisplayTime().takeUnless { isAllDay },
                    onDateClick = { picker = PickerTarget.StartDate },
                    onTimeClick = { picker = PickerTarget.StartTime }
                )
                DateTimeRow(
                    label = "Termina",
                    date = endDate.toFormDate(),
                    time = endTime.toDisplayTime().takeUnless { isAllDay },
                    onDateClick = { picker = PickerTarget.EndDate },
                    onTimeClick = { picker = PickerTarget.EndTime }
                )
                Text(
                    text = when {
                        !isTimeRangeValid && endDate == startDate ->
                            "El evento termina antes de empezar. Si acaba al día siguiente, cambia la fecha de fin"
                        !isTimeRangeValid -> "El evento no puede terminar antes de empezar"
                        else -> durationText(startDate.atTime(effectiveStartTime), endDate.atTime(effectiveEndTime), isAllDay)
                    },
                    color = if (isTimeRangeValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 2.dp)
                )
            }

            // Categoría
            Column {
                SectionTitle(Icons.Rounded.LocalOffer, "Categoría")
                CategoryChips(categories, selectedCategory, onSelect = { selectedCategory = it })
            }

            // Prioridad
            Column {
                SectionTitle(Icons.Rounded.Flag, "Prioridad")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    PRIORITIES_IN_ORDER.forEachIndexed { index, priority ->
                        SegmentedButton(
                            selected = selectedPriority == priority,
                            onClick = { selectedPriority = priority },
                            shape = SegmentedButtonDefaults.itemShape(index, PRIORITIES_IN_ORDER.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = priority.color.copy(alpha = 0.2f),
                                activeContentColor = MaterialTheme.colorScheme.onSurface,
                                activeBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                inactiveBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            ),
                            icon = {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(priority.color)
                                )
                            },
                            label = {
                                Text(
                                    priority.label.lowercase().replaceFirstChar { it.uppercase() },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }

            // Dónde y notas
            DetailCard {
                FieldRow(
                    icon = Icons.Rounded.Place,
                    accent = accent,
                    value = location,
                    onValueChange = { location = it },
                    placeholder = "Añadir ubicación",
                    singleLine = true
                )
                InfoDivider()
                FieldRow(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    accent = accent,
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Añadir descripción o notas...",
                    singleLine = false
                )
            }

            // Subtareas
            Column {
                SectionTitle(
                    Icons.Rounded.Checklist,
                    "Subtareas",
                    trailing = subtasks.size.takeIf { it > 0 }?.toString()
                )
                DetailCard {
                    subtasks.forEachIndexed { index, subtask ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = accent.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = subtask,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { subtasks.removeAt(index) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Quitar subtarea",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        InfoDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = subtaskInput,
                            onValueChange = { subtaskInput = it },
                            placeholder = {
                                Text("Añadir subtarea", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            },
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            // Intro añade la subtarea y deja el campo listo para la siguiente
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addSubtask() }),
                            colors = transparentTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        if (subtaskInput.isNotBlank()) {
                            FilledTonalIconButton(
                                onClick = { addSubtask() },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = accent.copy(alpha = 0.15f),
                                    contentColor = accent
                                )
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Añadir")
                            }
                        }
                    }
                }
            }
        }
    }

    when (val target = picker) {
        PickerTarget.StartDate, PickerTarget.EndDate -> {
            val isStart = target == PickerTarget.StartDate
            val datePickerState = rememberDatePickerState(
                // El DatePicker trabaja en UTC: con la zona local podía preseleccionar el día anterior
                initialSelectedDateMillis = (if (isStart) startDate else endDate)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            if (isStart) moveStart(date.atTime(startTime)) else endDate = date
                        }
                        picker = null
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) { Text("Cancelar") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        PickerTarget.StartTime, PickerTarget.EndTime -> {
            val isStart = target == PickerTarget.StartTime
            val initialTime = if (isStart) startTime else endTime
            val timePickerState = rememberTimePickerState(
                initialHour = initialTime.hour,
                initialMinute = initialTime.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        if (isStart) moveStart(startDate.atTime(time)) else endTime = time
                        picker = null
                    }) { Text("Aceptar") }
                },
                dismissButton = {
                    TextButton(onClick = { picker = null }) { Text("Cancelar") }
                },
                text = { TimePicker(state = timePickerState) }
            )
        }
        null -> Unit
    }
}

/** Chips de categoría, cada uno con su color e icono. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(categories: List<CategoryItem>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = selected == category.name
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(category.name) },
                label = { Text(category.name, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.color.copy(alpha = 0.18f),
                    selectedLabelColor = category.color,
                    selectedLeadingIconColor = category.color,
                    iconColor = category.color
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    selectedBorderColor = category.color.copy(alpha = 0.6f)
                )
            )
        }
    }
}

/** Fila "Empieza"/"Termina" con dos botones separados: la fecha y (si no es todo el día) la hora. */
@Composable
private fun DateTimeRow(
    label: String,
    date: String,
    time: String?,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        PickerButton(date, onDateClick)
        if (time != null) {
            Spacer(Modifier.width(8.dp))
            PickerButton(time, onTimeClick)
        }
    }
}

@Composable
internal fun PickerButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}

/** Icono en un cuadrado suave del color del evento, igual que en el detalle. */
@Composable
internal fun IconBadge(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun FieldRow(
    icon: ImageVector,
    accent: Color,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        Box(Modifier.padding(top = if (singleLine) 0.dp else 8.dp)) { IconBadge(icon, accent) }
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = transparentTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
fun Modifier.clickableWithoutRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}

data class CategoryItem(
    val name: String,
    val icon: ImageVector,
    val color: Color
)

// region Reglas de fecha del formulario

private val FORM_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("es", "ES"))

private fun LocalDate.toFormDate(): String = format(FORM_DATE).replaceFirstChar { it.uppercase() }

/** Próxima hora en punto: a las 10:20, las 11:00; a las 23:30, las 00:00 del día siguiente. */
internal fun nextFullHour(now: LocalDateTime): LocalDateTime = now.truncatedTo(ChronoUnit.HOURS).plusHours(1)

/**
 * Fin del evento tras mover su inicio a [newStart]: se conserva la duración. Si el rango actual no
 * es válido (fin antes del inicio), se deja una hora, que es la duración por defecto.
 */
internal fun endAfterMovingStart(oldStart: LocalDateTime, oldEnd: LocalDateTime, newStart: LocalDateTime): LocalDateTime {
    val duration = Duration.between(oldStart, oldEnd).takeUnless { it.isNegative } ?: Duration.ofHours(1)
    return newStart.plus(duration)
}

/** "Dura 1 h 30 min" o, en eventos de todo el día, "1 día" / "3 días". */
internal fun durationText(start: LocalDateTime, end: LocalDateTime, isAllDay: Boolean): String {
    if (isAllDay) {
        val days = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1
        return if (days == 1L) "1 día" else "$days días"
    }
    val minutes = Duration.between(start, end).toMinutes()
    return if (minutes == 0L) "Sin duración" else "Dura ${formatDuration(minutes)}"
}

// endregion
