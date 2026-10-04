package com.syncro.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.presentation.components.SheetDragHandle
import com.syncro.presentation.components.GradientSheetInsets
import com.syncro.domain.model.Recurrence
import com.syncro.presentation.components.DetailCard
import com.syncro.presentation.components.ReminderPicker
import com.syncro.presentation.components.RepeatPicker
import com.syncro.presentation.components.SectionTitle
import com.syncro.presentation.event.FieldRow
import com.syncro.presentation.event.IconBadge
import com.syncro.presentation.event.PickerButton
import com.syncro.presentation.event.transparentTextFieldColors
import com.syncro.presentation.theme.Emerald500
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hoja para crear una tarea. Las tareas solo tienen día (se guardan como "todo el día", igual que
 * las de Google Tasks), así que aquí no se pide hora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTaskSheet(
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, date: LocalDate, repeat: Recurrence?, reminderMinutes: Int?) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    // Inicio pasa su "hoy" (del reloj inyectado) para que no se quede en ayer tras la medianoche
    today: LocalDate = LocalDate.now()
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
        QuickTaskContent(initialDate = initialDate, onSave = onSave, today = today)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTaskContent(
    initialDate: LocalDate,
    onSave: (title: String, description: String, date: LocalDate, repeat: Recurrence?, reminderMinutes: Int?) -> Unit,
    today: LocalDate = LocalDate.now()
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var repeat by remember { mutableStateOf<Recurrence?>(null) }
    var reminderMinutes by remember { mutableStateOf<Int?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Las tareas no tienen categoría: siempre el verde de la tarjeta "Tareas" de Inicio
    val accent = Emerald500
    val canSave = title.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 40.dp)
    ) {
        // Cabecera: degradado como en el formulario de eventos
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
                    "Nueva tarea",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onSave(title, description, date, repeat, reminderMinutes) },
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
                        "¿Qué tienes que hacer?",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
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
            // Cuándo: solo el día
            DetailCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Rounded.Event, accent)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        date.toTaskDate(today),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DayChip("Hoy", selected = date == today, accent = accent) { date = today }
                    DayChip("Mañana", selected = date == today.plusDays(1), accent = accent) { date = today.plusDays(1) }
                    PickerButton("Otro día") { showDatePicker = true }
                }
            }

            // Notas
            DetailCard {
                FieldRow(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    accent = accent,
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "Añadir detalles...",
                    singleLine = false
                )
            }

            // Repetir: cada día, semana (con sus días), mes o año
            Column {
                SectionTitle(Icons.Rounded.Repeat, "Repetir")
                DetailCard { RepeatPicker(repeat = repeat, startDate = date, accent = accent, onChange = { repeat = it }) }
            }

            // Aviso: las tareas no tienen hora, así que se elige a qué hora del día (o del anterior)
            Column {
                SectionTitle(Icons.Rounded.NotificationsActive, "Aviso")
                DetailCard {
                    ReminderPicker(
                        reminderMinutes = reminderMinutes,
                        start = date.atStartOfDay(),
                        isAllDay = true,
                        accent = accent,
                        onChange = { reminderMinutes = it }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            // El DatePicker trabaja en UTC: con la zona local podía preseleccionar el día anterior
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontWeight = FontWeight.SemiBold) },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.18f),
            selectedLabelColor = accent
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            selectedBorderColor = accent.copy(alpha = 0.6f)
        ),
        modifier = Modifier.heightIn(min = 48.dp)
    )
}

private val TASK_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "ES"))

/** "Hoy, jueves 2 de octubre" / "Mañana, ..." / "Lunes, 6 de octubre". */
internal fun LocalDate.toTaskDate(today: LocalDate): String {
    val formatted = format(TASK_DATE)
    return when (this) {
        today -> "Hoy, ${formatted.replace(",", "")}"
        today.plusDays(1) -> "Mañana, ${formatted.replace(",", "")}"
        else -> formatted.replaceFirstChar { it.uppercase() }
    }
}
