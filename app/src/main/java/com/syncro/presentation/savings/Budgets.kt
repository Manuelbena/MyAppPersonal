package com.syncro.presentation.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.domain.model.BUDGET_WARNING_PERCENT
import com.syncro.domain.model.Budget
import com.syncro.domain.model.BudgetLevel
import com.syncro.domain.model.BudgetStatus
import com.syncro.domain.model.BudgetGroup
import com.syncro.domain.model.BudgetGroupSummary
import com.syncro.domain.model.BudgetPlan
import com.syncro.domain.model.SAVINGS_PERCENT
import com.syncro.domain.model.SavingsGoal
import com.syncro.domain.model.budgetGroup
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementOccurrence
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.label
import com.syncro.domain.model.parseEuroCents
import com.syncro.presentation.components.DeleteItemButton
import com.syncro.presentation.components.GradientSheetInsets
import com.syncro.presentation.components.SectionTitle
import com.syncro.presentation.components.SheetDragHandle
import com.syncro.presentation.event.IconBadge
import com.syncro.presentation.event.transparentTextFieldColors
import com.syncro.presentation.theme.Amber500
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.Rose500
import java.time.LocalDate

/*
 * Presupuestos en Ahorros: la tarjeta compacta con el 50/30/20 del mes en una sola barra, las
 * piezas del desglose (BudgetBreakdownScreen) y la hoja para crear, cambiar o quitar uno.
 */

/** Verde si va bien, ámbar desde el 80 % y rojo si se ha pasado. */
internal val BudgetLevel.color: Color
    get() = when (this) {
        BudgetLevel.OK -> Emerald500
        BudgetLevel.WARNING -> Amber500
        BudgetLevel.EXCEEDED -> Rose500
    }

/** Nombre y emoji de cada grupo del 50/30/20. */
internal val BudgetGroup.title: String
    get() = when (this) {
        BudgetGroup.NEEDS -> "🏠 Necesidades"
        BudgetGroup.WANTS -> "🎉 Caprichos"
    }

/** Nombre corto para la leyenda de la barra. */
internal val BudgetGroup.shortName: String
    get() = when (this) {
        BudgetGroup.NEEDS -> "Necesidades"
        BudgetGroup.WANTS -> "Caprichos"
    }

internal val BudgetGroup.hint: String
    get() = when (this) {
        BudgetGroup.NEEDS -> "Casa, facturas, comida, transporte, salud y estudios"
        BudgetGroup.WANTS -> "Salir, ocio, suscripciones, compras y otros"
    }

/** "Reparto 50/30/20 de tu nómina (2.000,00 €)"; null si este mes no ha entrado nada. */
internal fun BudgetPlan.baseLabel(): String? = base?.let {
    "Reparto 50/30/20 de ${if (it.isSalary) "tu nómina" else "lo que ha entrado"} (${formatEuros(it.cents)})"
}

/** Si hay algo que pintar en la barra: ha entrado dinero o se ha gastado algo. */
private val BudgetPlan.hasData: Boolean
    get() = base != null || groups.any { it.spentCents > 0 }

/**
 * Tarjeta "Presupuestos" de Ahorros, en pequeño: el mes como una pila (Necesidades, Caprichos y
 * lo que queda de Ahorro) con su leyenda, un aviso si algún presupuesto va justo o pasado, y
 * "Ver desglose" para las categorías y los presupuestos en su propia pantalla.
 */
@Composable
fun BudgetsCard(
    plan: BudgetPlan,
    onSeeBreakdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onSeeBreakdown,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "🎯 Presupuestos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                // Como el "Ver todas" de la libreta
                TextButton(onClick = onSeeBreakdown) {
                    Text("Ver desglose", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
            Column(modifier = Modifier.padding(end = 12.dp)) {
                if (plan.hasData) {
                    plan.baseLabel()?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                    }
                    BudgetStack(plan)
                    Spacer(Modifier.height(12.dp))
                    BudgetStackLegend(plan)
                    attentionNote(plan)?.let { (text, color) ->
                        Spacer(Modifier.height(12.dp))
                        Text(text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
                    }
                } else {
                    Text(
                        "Pon un límite a lo que gastas cada mes por categoría y te aviso al llegar al $BUDGET_WARNING_PERCENT %.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "⚠️ 1 presupuesto pasado · 1 casi al límite", o null si todos van bien. */
private fun attentionNote(plan: BudgetPlan): Pair<String, Color>? {
    val statuses = plan.groups.flatMap { it.statuses }
    val exceeded = statuses.count { it.level == BudgetLevel.EXCEEDED }
    val warning = statuses.count { it.level == BudgetLevel.WARNING }
    if (exceeded == 0 && warning == 0) return null
    val parts = listOfNotNull(
        exceeded.takeIf { it > 0 }?.let { if (it == 1) "1 presupuesto pasado" else "$it presupuestos pasados" },
        warning.takeIf { it > 0 }?.let { if (it == 1) "1 casi al límite" else "$it casi al límite" }
    )
    return "⚠️ ${parts.joinToString(" · ")}" to if (exceeded > 0) Rose500 else Amber500
}

/** Un trozo de la pila: su color y lo que pesa. */
private data class StackSegment(val color: Color, val cents: Long)

/**
 * El mes como una pila: lo gastado en Necesidades, en Caprichos y lo que queda (Ahorro), cada uno
 * del ancho que le toca. Sin ingresos solo hay gastos; gastando más de lo que entra no hay ahorro.
 */
@Composable
internal fun BudgetStack(plan: BudgetPlan, modifier: Modifier = Modifier) {
    val palette = budgetChartPalette
    val segments = plan.groups.map { StackSegment(palette.of(it.group), it.spentCents) } +
        listOfNotNull(plan.savings?.let { StackSegment(palette.savings, it.savedCents.coerceAtLeast(0)) })
    val visible = segments.filter { it.cents > 0 }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
            .semantics { contentDescription = "Reparto del mes" },
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        visible.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.cents.toFloat())
                    .fillMaxHeight()
                    .background(segment.color)
            )
        }
    }
}

/** Debajo de la barra: cada trozo con su color, lo que lleva y lo que le toca. */
@Composable
internal fun BudgetStackLegend(plan: BudgetPlan) {
    val palette = budgetChartPalette
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        plan.groups.forEach { group ->
            LegendItem(
                color = palette.of(group.group),
                label = group.group.shortName,
                amount = formatEuros(group.spentCents),
                // Solo se colorea lo que hay que mirar
                amountColor = group.level.takeIf { it != BudgetLevel.OK }?.color,
                detail = group.targetCents?.let { "de ${formatEuros(it)}" },
                modifier = Modifier.weight(1f)
            )
        }
        plan.savings?.let { goal ->
            LegendItem(
                color = palette.savings,
                label = "Ahorro",
                amount = (if (goal.savedCents < 0) "−" else "") + formatEuros(kotlin.math.abs(goal.savedCents)),
                amountColor = if (goal.savedCents < 0) Rose500 else null,
                detail = "de ${formatEuros(goal.targetCents)}",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    amount: String,
    amountColor: Color?,
    detail: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            amount,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = amountColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Cabecera de un grupo en el desglose: nombre y %, lo gastado frente a lo que le toca. */
@Composable
internal fun BudgetGroupHeader(summary: BudgetGroupSummary) {
    val levelColor = summary.level.color
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            "${summary.group.title} · ${summary.group.percent} %",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(summary.group.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        val target = summary.targetCents
        if (target != null) {
            BudgetBar(((summary.percent ?: 0) / 100f), levelColor)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    "${formatEuros(summary.spentCents)} de ${formatEuros(target)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                val left = target - summary.spentCents
                Text(
                    if (left >= 0) "Te quedan ${formatEuros(left)}" else "Te has pasado ${formatEuros(-left)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (left < 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (left < 0) Rose500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Sin ingresos este mes no hay 50/30/20 que calcular: solo lo gastado
            Text(
                "Llevas ${formatEuros(summary.spentCents)} este mes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetBar(progress: Float, color: Color) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.15f),
        drawStopIndicator = {}
    )
}

/** El 20 % que debería quedar: lo ahorrado (ingresos − gastos) frente a la meta. */
@Composable
internal fun SavingsGoalRow(goal: SavingsGoal) {
    val color = if (goal.savedCents < 0) Rose500 else Emerald500
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            "🐷 Ahorro · $SAVINGS_PERCENT %",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text("Lo que te queda de lo que ha entrado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BudgetBar(goal.percent / 100f, color)
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                "${formatEuros(goal.savedCents.coerceAtLeast(0))} de ${formatEuros(goal.targetCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                when {
                    goal.savedCents < 0 -> "Gastas más de lo que entra"
                    goal.reached -> "¡Objetivo cumplido! 💪"
                    else -> "Te faltan ${formatEuros(goal.targetCents - goal.savedCents)}"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (goal.reached || goal.savedCents < 0) FontWeight.Bold else FontWeight.Normal,
                color = if (goal.reached) Emerald500 else if (goal.savedCents < 0) Rose500 else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Una categoría con presupuesto: icono, nombre, "120,00 € de 300,00 €", la barra y lo que queda (o lo pasado). */
@Composable
internal fun BudgetRow(status: BudgetStatus, onClick: () -> Unit) {
    val category = status.budget.category
    val levelColor = status.level.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(category.icon, category.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${status.percent} %",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = levelColor
                )
            }
            Spacer(Modifier.height(6.dp))
            BudgetBar(status.percent / 100f, levelColor)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(
                    "${formatEuros(status.spentCents)} de ${formatEuros(status.budget.limitCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (status.remainingCents >= 0) "Te quedan ${formatEuros(status.remainingCents)}"
                    else "Te has pasado ${formatEuros(-status.remainingCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (status.level == BudgetLevel.EXCEEDED) FontWeight.Bold else FontWeight.Normal,
                    color = if (status.level == BudgetLevel.EXCEEDED) Rose500 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Una categoría con gastos y sin presupuesto: lo gastado y, si se toca, la hoja para ponerle un
 * límite ya con la categoría elegida.
 */
@Composable
internal fun UnbudgetedRow(category: MovementCategory, spentCents: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(category.icon, category.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Sin límite · Toca para poner uno",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatEuros(spentCents),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Crear (sin [existing]) o cambiar un presupuesto. Al crear se elige la categoría entre las de
 * gasto que aún no tienen ([available]); al cambiar, la categoría queda fija y se puede quitar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSheet(
    existing: Budget?,
    available: List<MovementCategory>,
    onDismiss: () -> Unit,
    onSave: (MovementCategory, Long) -> Unit,
    onDelete: (MovementCategory) -> Unit,
    // Al crear desde una categoría del desglose, ya viene elegida
    initialCategory: MovementCategory? = null,
    // Los gastos del mes que se ve: la hoja enseña los de la categoría elegida
    expenses: List<MovementOccurrence> = emptyList(),
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
        BudgetContent(existing, available, onSave, onDelete, initialCategory, expenses, today)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetContent(
    existing: Budget?,
    available: List<MovementCategory>,
    onSave: (MovementCategory, Long) -> Unit,
    onDelete: (MovementCategory) -> Unit,
    initialCategory: MovementCategory? = null,
    expenses: List<MovementOccurrence> = emptyList(),
    today: LocalDate = LocalDate.now()
) {
    var amountText by remember { mutableStateOf(existing?.limitCents?.toAmountInput().orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: initialCategory) }
    val limitCents = parseEuroCents(amountText)
    val accent = category?.color ?: MaterialTheme.colorScheme.primary
    val canSave = limitCents != null && category != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 40.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)))
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp)
        ) {
            SheetDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    existing?.let { "Presupuesto de ${it.category.label}" } ?: "Nuevo presupuesto",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { if (limitCents != null && category != null) onSave(category!!, limitCents) },
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
            Text(
                "al mes · te aviso al llegar al $BUDGET_WARNING_PERCENT %",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (existing == null) {
                Column {
                    SectionTitle(Icons.Rounded.LocalOffer, "Categoría")
                    // Por grupos del 50/30/20, como en el desglose: la lista se lee mejor
                    BudgetGroup.entries.forEach { group ->
                        val options = available.filter { it.budgetGroup == group }
                        if (options.isEmpty()) return@forEach
                        Text(
                            "${group.title} · ${group.percent} %",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            options.forEach { option ->
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
                }
            }
            category?.let { CategoryMovements(it, expenses, today) }
            if (existing != null) {
                DeleteItemButton(
                    label = "Quitar presupuesto",
                    confirmTitle = "¿Quitar el presupuesto de ${existing.category.label}?",
                    warning = "Tus gastos no se tocan: solo dejarás de ver el límite y sus avisos.",
                    onDelete = { onDelete(existing.category) }
                )
            }
        }
    }
}

/**
 * Los gastos del mes en [category], del más reciente al más antiguo, con su total: lo que cuenta
 * para el presupuesto (también los previstos, atenuados).
 */
@Composable
private fun CategoryMovements(category: MovementCategory, expenses: List<MovementOccurrence>, today: LocalDate) {
    val ofCategory = expenses
        .filter { it.movement.type == MovementType.EXPENSE && it.movement.category == category }
        .sortedByDescending { it.date }
    Column {
        SectionTitle(
            Icons.AutoMirrored.Rounded.ReceiptLong,
            "Movimientos del mes",
            trailing = formatEuros(ofCategory.sumOf { it.movement.amountCents }).takeIf { ofCategory.isNotEmpty() }
        )
        if (ofCategory.isEmpty()) {
            Text(
                "Este mes aún no hay gastos en ${category.label}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            ofCategory.forEach { occurrence ->
                MovementRow(occurrence, isUpcoming = occurrence.date.isAfter(today), onClick = null, horizontalPadding = 0.dp)
            }
        }
    }
}

/** Categorías de gasto que aún no tienen presupuesto (las que se pueden elegir al crear uno). */
fun categoriesWithoutBudget(budgets: List<Budget>): List<MovementCategory> =
    MovementCategory.of(MovementType.EXPENSE).filter { category -> budgets.none { it.category == category } }
