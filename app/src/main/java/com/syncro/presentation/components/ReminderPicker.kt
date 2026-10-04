package com.syncro.presentation.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.reminderMinutesFor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * Avisos de tareas y eventos en el formulario y en el detalle. El aviso se guarda en minutos antes
 * de empezar; aquí se elige con unos tramos rápidos o con "Otra hora…" (día y hora exactos).
 */

private val SPANISH = Locale("es", "ES")
private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** Un tramo rápido: [chip] en el formulario, [label] en el detalle. */
private data class ReminderPreset(val minutes: Int, val chip: String, val label: String)

/** Con hora: antes de empezar. */
private val TIMED_PRESETS = listOf(
    ReminderPreset(0, "Al empezar", "Al empezar"),
    ReminderPreset(5, "5 min", "5 min antes"),
    ReminderPreset(15, "15 min", "15 min antes"),
    ReminderPreset(30, "30 min", "30 min antes"),
    ReminderPreset(60, "1 h", "1 h antes"),
    ReminderPreset(24 * 60, "1 día", "1 día antes")
)

/** Todo el día (y las tareas, que no tienen hora): "a las 00:00 menos X" no tiene sentido, se dan horas del día. */
private val ALL_DAY_PRESETS = listOf(
    ReminderPreset(-9 * 60, "Ese día a las 9:00", "Ese día a las 9:00"),
    ReminderPreset(4 * 60, "El día antes a las 20:00", "El día antes a las 20:00")
)

private fun presetsFor(isAllDay: Boolean) = if (isAllDay) ALL_DAY_PRESETS else TIMED_PRESETS

/** Al pasar un evento con aviso a "todo el día" (o al revés), el aviso equivalente: 15 min antes / ese día a las 9:00. */
const val TIMED_DEFAULT_REMINDER = 15
const val ALL_DAY_DEFAULT_REMINDER = -9 * 60

/** "hoy a las 18:15", "mañana a las 9:00", "el lunes 5 de octubre a las 18:15". */
fun LocalDateTime.reminderMoment(today: LocalDate): String {
    val day = toLocalDate()
    val dayText = when (day) {
        today -> "hoy"
        today.plusDays(1) -> "mañana"
        today.minusDays(1) -> "ayer"
        else -> "el ${day.dayOfWeek.getDisplayName(TextStyle.FULL, SPANISH)} ${day.dayOfMonth} de ${day.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH)}"
    }
    return "$dayText a las ${format(TIME)}"
}

/** Para el detalle: "15 min antes", "Ese día a las 9:00" o, si es una hora elegida, "El lunes 5 de octubre a las 18:15". */
fun reminderLabel(minutes: Int, start: LocalDateTime, isAllDay: Boolean, today: LocalDate): String =
    presetsFor(isAllDay).firstOrNull { it.minutes == minutes }?.label
        ?: start.minusMinutes(minutes.toLong()).reminderMoment(today).replaceFirstChar { it.uppercase() }

private enum class CustomStep { Date, Time }

/**
 * "Aviso" del formulario: sin aviso, unos tramos rápidos o "Otra hora…" (día y luego hora). Debajo
 * se dice cuándo sonará y, si las notificaciones están desactivadas, se ofrece activarlas.
 *
 * @param start cuándo empieza (en tareas y eventos de todo el día, las 00:00 de su día)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(
    reminderMinutes: Int?,
    start: LocalDateTime,
    isAllDay: Boolean,
    accent: Color,
    onChange: (Int?) -> Unit,
    now: LocalDateTime = LocalDateTime.now()
) {
    val presets = presetsFor(isAllDay)
    val isCustom = reminderMinutes != null && presets.none { it.minutes == reminderMinutes }
    var customStep by remember { mutableStateOf<CustomStep?>(null) }
    var customDate by remember { mutableStateOf(start.toLocalDate()) }

    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderChip("Sin aviso", selected = reminderMinutes == null, accent = accent) { onChange(null) }
            presets.forEach { preset ->
                ReminderChip(preset.chip, selected = reminderMinutes == preset.minutes, accent = accent) { onChange(preset.minutes) }
            }
            ReminderChip("Otra hora…", selected = isCustom, accent = accent) {
                customDate = reminderMinutes?.let { start.minusMinutes(it.toLong()).toLocalDate() } ?: start.toLocalDate()
                customStep = CustomStep.Date
            }
        }

        if (reminderMinutes == null) {
            Text("No te avisaremos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val at = start.minusMinutes(reminderMinutes.toLong())
            if (at.isBefore(now)) {
                Text(
                    "Esa hora ya ha pasado: no te llegará el aviso",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    "Te avisaremos ${at.reminderMoment(now.toLocalDate())}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "Te avisa Syncro en este móvil (Google no te enviará otro aviso)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NotificationsOffHint()
        }
    }

    when (customStep) {
        CustomStep.Date -> {
            val state = rememberDatePickerState(
                // El DatePicker trabaja en UTC: con la zona local podía preseleccionar el día anterior
                initialSelectedDateMillis = customDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { customStep = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { customDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        customStep = CustomStep.Time
                    }) { Text("Siguiente") }
                },
                dismissButton = { TextButton(onClick = { customStep = null }) { Text("Cancelar") } }
            ) {
                DatePicker(state = state, title = { Text("¿Qué día te avisamos?", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
            }
        }
        CustomStep.Time -> {
            val initial = reminderMinutes?.let { start.minusMinutes(it.toLong()).toLocalTime() } ?: start.toLocalTime().minusHours(1)
            val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { customStep = null },
                title = { Text("¿A qué hora?") },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        onChange(reminderMinutesFor(start, customDate.atTime(LocalTime.of(state.hour, state.minute))))
                        customStep = null
                    }) { Text("Aceptar") }
                },
                dismissButton = { TextButton(onClick = { customStep = null }) { Text("Cancelar") } }
            )
        }
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
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
        )
    )
}

/**
 * Si Android no deja publicar avisos, se dice aquí mismo y se ofrece activarlos: el diálogo del
 * permiso mientras Android aún lo enseñe, si no los ajustes del sistema (como en Ajustes y el chat).
 */
@Composable
private fun NotificationsOffHint() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(context.notificationsAllowed()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = context.notificationsAllowed()
    }
    if (allowed) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Tienes las notificaciones desactivadas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.canAskNotificationPermission()) {
                context.markNotificationPermissionAsked()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openNotificationSettings()
            }
        }) { Text("Activar") }
    }
}
