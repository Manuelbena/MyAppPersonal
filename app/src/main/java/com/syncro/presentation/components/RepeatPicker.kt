package com.syncro.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.Recurrence
import com.syncro.domain.model.RepeatFrequency
import com.syncro.domain.model.RepeatScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * Repetir tareas y eventos: el selector del formulario, el texto que explica la regla y el diálogo
 * de "solo esta / esta y las siguientes" al cambiar o borrar una repetición.
 */

private val SPANISH = Locale("es", "ES")

/** La semana empieza en lunes, como en España. */
private val WEEK = DayOfWeek.entries

private val WORKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

/** L M X J V S D: la X es el miércoles, para no confundirlo con el martes (como en los calendarios en español). */
val DayOfWeek.initial: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "L"
        DayOfWeek.TUESDAY -> "M"
        DayOfWeek.WEDNESDAY -> "X"
        DayOfWeek.THURSDAY -> "J"
        DayOfWeek.FRIDAY -> "V"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "D"
    }

private val DayOfWeek.fullName: String get() = getDisplayName(TextStyle.FULL, SPANISH)

/** "lunes, miércoles y viernes". */
private fun List<String>.joinedSpanish(): String =
    if (size <= 1) joinToString() else dropLast(1).joinToString(", ") + " y " + last()

/**
 * La regla en palabras, para el formulario y el detalle: "Cada día", "Cada semana: lunes y
 * jueves", "De lunes a viernes", "Cada mes, el día 27", "Cada año, el 27 de septiembre". [start] es
 * el primer día de la serie (del que salen el día del mes y la fecha del año).
 */
fun Recurrence.describe(start: LocalDate): String = when (frequency) {
    RepeatFrequency.DAILY -> "Cada día"
    RepeatFrequency.WEEKLY -> when (weekdays) {
        WEEK.toSet() -> "Cada día de la semana"
        WORKDAYS -> "De lunes a viernes"
        else -> "Cada semana: " + WEEK.filter { it in weekdays }.map { it.fullName }.joinedSpanish()
    }
    RepeatFrequency.MONTHLY -> "Cada mes, el día ${start.dayOfMonth}" + if (start.dayOfMonth > 28) " (o el último del mes)" else ""
    RepeatFrequency.YEARLY -> "Cada año, el ${start.dayOfMonth} de ${start.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH)}"
}

/** Lo corto para una píldora: "Cada semana", "Cada mes"… */
val Recurrence.shortLabel: String
    get() = when (frequency) {
        RepeatFrequency.DAILY -> "Cada día"
        RepeatFrequency.WEEKLY -> "Cada semana"
        RepeatFrequency.MONTHLY -> "Cada mes"
        RepeatFrequency.YEARLY -> "Cada año"
    }

private data class RepeatOption(val label: String, val frequency: RepeatFrequency?)

private val REPEAT_OPTIONS = listOf(
    RepeatOption("No", null),
    RepeatOption("Cada día", RepeatFrequency.DAILY),
    RepeatOption("Cada semana", RepeatFrequency.WEEKLY),
    RepeatOption("Cada mes", RepeatFrequency.MONTHLY),
    RepeatOption("Cada año", RepeatFrequency.YEARLY)
)

/**
 * "Repetir" del formulario: no, cada día, cada semana (eligiendo los días, L M X J V S D), cada mes
 * o cada año, con la regla explicada debajo. [startDate] es el día elegido en el formulario: al
 * pasar a "Cada semana" se marca su día de la semana.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatPicker(
    repeat: Recurrence?,
    startDate: LocalDate,
    accent: Color,
    onChange: (Recurrence?) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            REPEAT_OPTIONS.forEach { option ->
                val selected = repeat?.frequency == option.frequency
                FilterChip(
                    selected = selected,
                    onClick = {
                        onChange(
                            when (val frequency = option.frequency) {
                                null -> null
                                repeat?.frequency -> repeat
                                RepeatFrequency.WEEKLY -> Recurrence.weeklyOn(startDate)
                                else -> Recurrence(frequency)
                            }
                        )
                    },
                    label = { Text(option.label, fontWeight = FontWeight.SemiBold) },
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
        }

        if (repeat?.frequency == RepeatFrequency.WEEKLY) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEK.forEach { day ->
                    val selected = day in repeat.weekdays
                    Surface(
                        // Siempre queda al menos un día: quitar el último no hace nada
                        onClick = {
                            val days = if (selected) repeat.weekdays - day else repeat.weekdays + day
                            if (days.isNotEmpty()) onChange(repeat.copy(weekdays = days))
                        },
                        shape = CircleShape,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .semantics {
                                contentDescription = day.fullName
                                this.selected = selected
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(day.initial, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        Text(
            repeat?.describe(startDate) ?: "No se repite",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Al cambiar o borrar una repetición: ¿solo esta o también las siguientes? [feminine] para las
 * tareas ("Solo esta") y no para los eventos ("Solo este").
 */
@Composable
fun RepeatScopeDialog(
    title: String,
    text: String,
    feminine: Boolean,
    onChoose: (RepeatScope) -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onChoose(RepeatScope.THIS) }) {
                    Text(if (feminine) "Solo esta" else "Solo este", color = color)
                }
                TextButton(onClick = { onChoose(RepeatScope.THIS_AND_FOLLOWING) }) {
                    Text(if (feminine) "Esta y las siguientes" else "Este y los siguientes", color = color)
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}
