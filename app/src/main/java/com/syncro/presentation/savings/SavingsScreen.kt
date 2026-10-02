package com.syncro.presentation.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.Movement
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.presentation.components.AddFab
import com.syncro.presentation.event.IconBadge
import com.syncro.presentation.home.components.AddOptionItem
import com.syncro.presentation.home.components.AddOptionsSheet
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.Rose500
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SPANISH = Locale("es", "ES")

@Composable
fun SavingsScreen(viewModel: SavingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val month by viewModel.currentMonth.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    SavingsContent(
        month = month,
        movements = state,
        today = viewModel.today,
        snackbarHostState = snackbarHostState,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onPrint = { context.printMovementTicket(it, LocalDateTime.now()) }
    )
}

/** Qué movimiento se está viendo en detalle: el id y el día (un mensual sale en varios meses). */
private data class SelectedOccurrence(val id: String, val date: LocalDate)

/**
 * Ahorros: el mes con su resumen (ingresos, gastos, balance y tasa de ahorro), sus movimientos
 * por día y el "+" para apuntar uno nuevo. Tocar un movimiento abre su detalle, desde donde se
 * edita, se imprime su ticket o se borra.
 */
@Composable
fun SavingsContent(
    month: YearMonth,
    movements: MonthMovements?,
    today: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSave: (type: MovementType, amountCents: Long, category: MovementCategory, date: LocalDate, note: String, repeatsMonthly: Boolean, id: String?) -> Unit,
    onDelete: (id: String) -> Unit,
    onPrint: (MovementOccurrence) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var formType by remember { mutableStateOf<MovementType?>(null) }
    // Se guarda el id y no el movimiento para que el detalle refleje los cambios al editarlo
    var selected by remember { mutableStateOf<SelectedOccurrence?>(null) }
    var editing by remember { mutableStateOf<Movement?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = 110.dp)) },
        floatingActionButton = { AddFab(onClick = { showAddSheet = true }, contentDescription = "Añadir movimiento") },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Abajo deja sitio a la barra de navegación flotante y al "+"
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            item(key = "header") {
                SavingsHeader(month, onPreviousMonth, onNextMonth)
            }
            if (movements != null) {
                item(key = "summary") { MonthSummaryCard(movements) }

                if (movements.occurrences.isEmpty()) {
                    item(key = "empty") { EmptyMonth() }
                } else {
                    movements.occurrences.groupBy { it.date }.forEach { (date, dayItems) ->
                        item(key = "day-$date") { DayHeader(date, today) }
                        items(dayItems, key = { "${it.movement.id}-${it.date}" }) { occurrence ->
                            MovementRow(
                                occurrence = occurrence,
                                isUpcoming = occurrence.date.isAfter(today),
                                onClick = { selected = SelectedOccurrence(occurrence.movement.id, occurrence.date) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddMovementSheet(
            onDismiss = { showAddSheet = false },
            onSelect = { type ->
                showAddSheet = false
                formType = type
            }
        )
    }

    formType?.let { type ->
        MovementSheet(
            type = type,
            today = today,
            onDismiss = { formType = null },
            onSave = { amountCents, category, date, note, repeatsMonthly ->
                formType = null
                onSave(type, amountCents, category, date, note, repeatsMonthly, null)
            }
        )
    }

    editing?.let { movement ->
        MovementSheet(
            type = movement.type,
            today = today,
            initial = movement,
            onDismiss = { editing = null },
            onSave = { amountCents, category, date, note, repeatsMonthly ->
                editing = null
                onSave(movement.type, amountCents, category, date, note, repeatsMonthly, movement.id)
            }
        )
    }

    selected
        ?.let { sel -> movements?.occurrences?.firstOrNull { it.movement.id == sel.id && it.date == sel.date } }
        ?.let { occurrence ->
            MovementDetailSheet(
                occurrence = occurrence,
                onDismiss = { selected = null },
                onEdit = {
                    selected = null
                    editing = occurrence.movement
                },
                onPrint = { onPrint(occurrence) },
                onDelete = {
                    selected = null
                    onDelete(occurrence.movement.id)
                }
            )
        }
}

/** Hoja del "+" de Ahorros, con el mismo aspecto que la de "Crear nuevo" de Inicio. */
@Composable
fun AddMovementSheet(onDismiss: () -> Unit, onSelect: (MovementType) -> Unit) {
    AddOptionsSheet(title = "Nuevo movimiento", onDismiss = onDismiss) {
        AddOptionItem(
            title = "Nuevo ingreso",
            description = "Nómina, ventas, devoluciones… El dinero que entra.",
            icon = Icons.AutoMirrored.Rounded.TrendingUp,
            iconColor = Emerald500,
            onClick = { onSelect(MovementType.INCOME) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        AddOptionItem(
            title = "Nuevo gasto",
            description = "Compras, facturas, suscripciones… El dinero que sale.",
            icon = Icons.AutoMirrored.Rounded.TrendingDown,
            iconColor = Rose500,
            onClick = { onSelect(MovementType.EXPENSE) }
        )
    }
}

/** Título y el mes que se ve, con flechas para ir al anterior o al siguiente. */
@Composable
private fun SavingsHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            "Ahorros",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH).replaceFirstChar { it.uppercase() } + " ${month.year}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mes anterior")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Mes siguiente")
            }
        }
    }
}

/**
 * Resumen del mes como lo vería un banco: el balance en grande, lo que entra y lo que sale, y la
 * tasa de ahorro (qué parte de lo que entra se queda).
 */
@Composable
private fun MonthSummaryCard(movements: MonthMovements) {
    val balance = movements.balanceCents
    val balanceColor = when {
        balance > 0 -> Emerald500
        balance < 0 -> Rose500
        else -> MaterialTheme.colorScheme.onSurface
    }
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent)))
                .padding(20.dp)
        ) {
            Text(
                "Balance del mes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                (if (balance > 0) "+" else if (balance < 0) "−" else "") + formatEuros(kotlin.math.abs(balance)),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = balanceColor
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalTile("Ingresos", formatEuros(movements.incomeCents), Emerald500, Modifier.weight(1f))
                TotalTile("Gastos", formatEuros(movements.expenseCents), Rose500, Modifier.weight(1f))
            }
            movements.savingsRatePercent?.let { rate ->
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        rate >= 20 -> "💪 Ahorras el $rate % de lo que ingresas. ¡Muy bien!"
                        rate > 0 -> "Ahorras el $rate % de lo que ingresas. Un buen objetivo es el 20 %."
                        rate == 0 -> "Este mes gastas todo lo que ingresas."
                        else -> "⚠️ Este mes gastas más de lo que ingresas."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, amount: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Text(
            amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val DAY_HEADER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    Text(
        when (date) {
            today -> "Hoy"
            today.minusDays(1) -> "Ayer"
            else -> date.format(DAY_HEADER).replaceFirstChar { it.uppercase() }
        },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp)
    )
}

/**
 * Un movimiento: icono de su categoría, concepto (o la categoría si no tiene), y el importe con
 * signo y color. Los que aún no han llegado este mes (un recibo a final de mes) salen atenuados
 * como "Previsto".
 */
@Composable
private fun MovementRow(occurrence: MovementOccurrence, isUpcoming: Boolean, onClick: () -> Unit) {
    val movement = occurrence.movement
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (isUpcoming) 0.6f else 1f)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(movement.category.icon, movement.category.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                movement.note ?: movement.category.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    // La categoría solo si no es ya el título (sin concepto, o "Nómina" en Nómina)
                    movement.category.label.takeIf { movement.note != null && !movement.note.equals(it, ignoreCase = true) },
                    "Cada mes".takeIf { movement.repeatsMonthly },
                    "Previsto".takeIf { isUpcoming }
                ).joinToString(" · ").ifEmpty { if (movement.type == MovementType.INCOME) "Ingreso" else "Gasto" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatSignedEuros(movement.amountCents, movement.type),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = movement.type.color
        )
    }
}

@Composable
private fun EmptyMonth() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Savings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Aún no hay movimientos",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Registra tus ingresos y gastos con el botón + para saber cuánto ahorras cada mes.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
