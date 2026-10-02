package com.syncro.presentation.savings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.presentation.components.SheetDragHandle
import com.syncro.presentation.components.GradientSheetInsets
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.parseEuroCents
import com.syncro.presentation.components.DetailCard
import com.syncro.presentation.components.InfoDivider
import com.syncro.presentation.components.SectionTitle
import com.syncro.presentation.event.FieldRow
import com.syncro.presentation.event.IconBadge
import com.syncro.presentation.event.PickerButton
import com.syncro.presentation.event.transparentTextFieldColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Lo que se teclea en el importe: hasta 10 cifras y, como mucho, dos decimales con coma o punto. */
private val AMOUNT_INPUT = Regex("""^\d{0,10}([.,]\d{0,2})?$""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementSheet(
    type: MovementType,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, category: MovementCategory, date: LocalDate, note: String, repeatsMonthly: Boolean) -> Unit
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
        MovementContent(type = type, today = today, onSave = onSave)
    }
}

/**
 * Formulario de un ingreso o gasto. Pensado para apuntarlo en segundos: el importe primero y en
 * grande, la categoría a un toque y "Hoy" ya elegido. "Guardar" se activa con importe y categoría.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MovementContent(
    type: MovementType,
    today: LocalDate,
    onSave: (amountCents: Long, category: MovementCategory, date: LocalDate, note: String, repeatsMonthly: Boolean) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<MovementCategory?>(null) }
    var date by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }
    var repeatsMonthly by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val accent = type.color
    val amountCents = parseEuroCents(amountText)
    val canSave = amountCents != null && category != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 40.dp)
    ) {
        // Cabecera: verde si entra dinero, rojo si sale; el importe en grande
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
                    if (type == MovementType.INCOME) "Nuevo ingreso" else "Nuevo gasto",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (amountCents != null && category != null) {
                            onSave(amountCents, category!!, date, note, repeatsMonthly)
                        }
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
                value = amountText,
                // Solo se acepta lo que puede ser un importe: así nunca hay que corregir letras
                onValueChange = { if (AMOUNT_INPUT.matches(it)) amountText = it },
                placeholder = {
                    Text(
                        "0,00 €",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                // El € va pegado al número, centrado con él (como suffix quedaba en el borde)
                visualTransformation = EuroSuffix,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = accent
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
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
            // Categoría (con "Otros" al final para lo que no encaje)
            Column {
                SectionTitle(Icons.Rounded.LocalOffer, "Categoría")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MovementCategory.of(type).forEach { option ->
                        val isSelected = category == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { category = option },
                            label = { Text(option.label, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                            leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = option.color.copy(alpha = 0.18f),
                                selectedLabelColor = option.color,
                                selectedLeadingIconColor = option.color,
                                iconColor = option.color
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                selectedBorderColor = option.color.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }

            // Cuándo y si se repite
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
                        date.toMovementDate(today),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lo normal es apuntar algo de hoy o que se olvidó ayer
                    DayChip("Hoy", selected = date == today, accent = accent) { date = today }
                    DayChip("Ayer", selected = date == today.minusDays(1), accent = accent) { date = today.minusDays(1) }
                    PickerButton("Otro día") { showDatePicker = true }
                }
                InfoDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBadge(Icons.Rounded.Autorenew, accent)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Se repite cada mes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (type == MovementType.INCOME) "Nómina, alquiler que cobras…" else "Alquiler, recibos, suscripciones…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = repeatsMonthly,
                        onCheckedChange = { repeatsMonthly = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
                    )
                }
            }

            // Concepto
            DetailCard {
                FieldRow(
                    icon = Icons.AutoMirrored.Rounded.Notes,
                    accent = accent,
                    value = note,
                    onValueChange = { note = it },
                    placeholder = if (type == MovementType.INCOME) "Concepto (opcional). Ej: Nómina de octubre" else "Concepto (opcional). Ej: Mercadona",
                    singleLine = true
                )
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

/** Muestra " €" detrás de lo tecleado sin que forme parte del texto (el cursor nunca pasa del número). */
private object EuroSuffix : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(
            AnnotatedString(text.text + " €"),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset
                override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(text.length)
            }
        )
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

private val MOVEMENT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", Locale("es", "ES"))

/** "Hoy, jueves 2 de octubre" / "Ayer, ..." / "Lunes, 28 de septiembre". */
internal fun LocalDate.toMovementDate(today: LocalDate): String {
    val formatted = format(MOVEMENT_DATE)
    return when (this) {
        today -> "Hoy, ${formatted.replace(",", "")}"
        today.minusDays(1) -> "Ayer, ${formatted.replace(",", "")}"
        else -> formatted.replaceFirstChar { it.uppercase() }
    }
}
