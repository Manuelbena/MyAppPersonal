package com.syncro.presentation.event

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncro.domain.model.Priority
import com.syncro.domain.model.isValidEventRange
import com.syncro.presentation.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventScreen(
    eventToEdit: com.syncro.domain.model.SyncroItem.Event? = null,
    onDismiss: () -> Unit,
    viewModel: com.syncro.presentation.home.HomeViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventContent(
    eventToEdit: com.syncro.domain.model.SyncroItem.Event? = null,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, LocalDate, LocalDate, LocalTime, LocalTime, String, Color, Priority?, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var description by remember { mutableStateOf(eventToEdit?.description ?: "") }
    var location by remember { mutableStateOf(eventToEdit?.location ?: "") }
    var selectedCategory by remember { mutableStateOf(eventToEdit?.categoryText ?: "Personal") }
    var selectedPriority by remember { mutableStateOf(eventToEdit?.priority ?: Priority.MEDIUM) }
    var isAllDay by remember { mutableStateOf(eventToEdit?.isAllDay == true) }

    // Subtareas
    var subtaskInput by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf(*eventToEdit?.subtasks?.map { it.title }?.toTypedArray() ?: arrayOf()) }

    var startDate by remember { mutableStateOf(eventToEdit?.date ?: LocalDate.now()) }
    
    // Evento nuevo: por defecto de la próxima hora en punto a la siguiente
    val parsedStartTime = eventToEdit?.startTime ?: LocalTime.now().withMinute(0).plusHours(1)
    val parsedEndTime = eventToEdit?.endTime ?: LocalTime.now().withMinute(0).plusHours(2)

    var startTime by remember { mutableStateOf(parsedStartTime) }
    var endDate by remember { mutableStateOf(eventToEdit?.endDate ?: LocalDate.now()) }
    var endTime by remember { mutableStateOf(parsedEndTime) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(true) }

    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM.", Locale("es", "ES"))
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    // "Todo el día" se guarda como 00:00–00:00: así lo entienden el resto de la app y la sync con Google
    val effectiveStartTime = if (isAllDay) LocalTime.MIDNIGHT else startTime
    val effectiveEndTime = if (isAllDay) LocalTime.MIDNIGHT else endTime
    // Se comparan fecha y hora: 21:30 → 01:00 del día siguiente es válido
    val isTimeRangeValid = isValidEventRange(startDate.atTime(effectiveStartTime), endDate.atTime(effectiveEndTime))
    val canSave = title.isNotBlank() && isTimeRangeValid

    val categories = listOf(
        CategoryItem("Personal", Icons.Rounded.Person, Emerald500),
        CategoryItem("Trabajo", Icons.Rounded.Work, Indigo500),
        CategoryItem("Salud", Icons.Rounded.Favorite, Color(0xFFFF5252)),
        CategoryItem("Ocio", Icons.Rounded.SportsEsports, Amber500)
    )

    val priorities = Priority.entries.toTypedArray()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (eventToEdit != null) "Editar evento" else "Nuevo evento",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
                        categories.find { it.name == selectedCategory }?.color ?: Emerald500,
                        selectedPriority, 
                        subtasks.toList()
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan900,
                    contentColor = Cyan400
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = canSave,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Título
        TextField(
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            value = title,
            onValueChange = { title = it },
            placeholder = { 
                Text(
                    "Añade un título", 
                    fontSize = 28.sp, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ) 
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Sección de Tiempo
        EventSection(icon = Icons.Rounded.Schedule) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DateTimeSelector(
                    label = "Empieza",
                    dateTime = if (isAllDay) startDate.format(dateFormatter) else "${startDate.format(dateFormatter)} • ${startTime.format(timeFormatter)}",
                    onClick = { 
                        pickingStart = true
                        showDatePicker = true 
                    }
                )
                DateTimeSelector(
                    label = "Termina",
                    dateTime = if (isAllDay) endDate.format(dateFormatter) else "${endDate.format(dateFormatter)} • ${endTime.format(timeFormatter)}",
                    onClick = { 
                        pickingStart = false
                        showDatePicker = true 
                    }
                )
                if (!isTimeRangeValid) {
                    Text(
                        if (endDate == startDate) "El evento termina antes de empezar. Si acaba al día siguiente, cambia la fecha de fin" else "El evento no puede terminar antes de empezar",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Todo el día", 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = isAllDay,
                        onCheckedChange = { isAllDay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Cyan400,
                            checkedTrackColor = Cyan900
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Categoría
        EventSection(icon = Icons.Rounded.LocalOffer) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { category ->
                    CategoryChip(
                        item = category,
                        isSelected = selectedCategory == category.name,
                        onClick = { selectedCategory = category.name }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Prioridad
        EventSection(icon = Icons.Rounded.Flag) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                priorities.forEach { priority ->
                    PriorityChip(
                        priority = priority,
                        isSelected = selectedPriority == priority,
                        onClick = { selectedPriority = priority }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Descripción
        EventSection(icon = Icons.AutoMirrored.Rounded.Notes) {
            TextField(
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                value = description,
                onValueChange = { description = it },
                placeholder = { 
                    Text(
                        "Añadir descripción o notas...",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Ubicación (Minimalista como título)
        EventSection(icon = Icons.Rounded.LocationOn) {
            TextField(
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                value = location,
                onValueChange = { location = it },
                placeholder = { 
                    Text(
                        "Añadir ubicación",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ) 
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = TextStyle(
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Subtareas Dinámicas
        EventSection(icon = Icons.Rounded.CheckCircle) {
            Column(modifier = Modifier.fillMaxWidth()) {
                subtasks.forEachIndexed { index, subtask ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = subtask,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { subtasks.removeAt(index) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Borrar",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        value = subtaskInput,
                        onValueChange = { subtaskInput = it },
                        placeholder = { 
                            Text(
                                "Añadir subtarea",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            ) 
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        textStyle = TextStyle(fontSize = 16.sp)
                    )
                    
                    if (subtaskInput.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (subtaskInput.isNotBlank()) {
                                    subtasks.add(subtaskInput)
                                    subtaskInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Cyan400.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Añadir",
                                tint = Cyan400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showDatePicker) {
        val initialDate = if (pickingStart) startDate else endDate
        val datePickerState = rememberDatePickerState(
            // El DatePicker trabaja en UTC: con la zona local podía preseleccionar el día anterior
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        val date = Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (pickingStart) {
                            // Mover el inicio arrastra el fin: se conserva la duración, como en Google Calendar
                            endDate = endDate.plusDays(ChronoUnit.DAYS.between(startDate, date))
                            startDate = date
                        } else {
                            endDate = date
                        }
                    }
                    showDatePicker = false
                    if (!isAllDay) showTimePicker = true
                }) { Text("Siguiente") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initialTime = if (pickingStart) startTime else endTime
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    if (pickingStart) startTime = time else endTime = time
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
fun EventSection(
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            content()
        }
    }
}

@Composable
fun DateTimeSelector(
    label: String,
    dateTime: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickableWithoutRipple(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dateTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryChip(
    item: CategoryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) item.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) item.color else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) item.color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline

    Surface(
        modifier = Modifier.clickableWithoutRipple(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.name,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PriorityChip(
    priority: Priority,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) priority.color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) priority.color else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isSelected) priority.color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline

    Surface(
        modifier = Modifier.clickableWithoutRipple(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = priority.label,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

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
