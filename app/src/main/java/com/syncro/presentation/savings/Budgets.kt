package com.syncro.presentation.savings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/*
 * Presupuestos en Ahorros: la tarjeta con cómo va cada uno este mes y la hoja para crear, cambiar
 * o quitar uno.
 */

/** Verde si va bien, ámbar desde el 80 % y rojo si se ha pasado. */
private val BudgetLevel.color: Color
    get() = when (this) {
        BudgetLevel.OK -> Emerald500
        BudgetLevel.WARNING -> Amber500
        BudgetLevel.EXCEEDED -> Rose500
    }

/** Nombre y emoji de cada grupo del 50/30/20. */
private val BudgetGroup.title: String
    get() = when (this) {
        BudgetGroup.NEEDS -> "🏠 Necesidades"
        BudgetGroup.WANTS -> "🎉 Caprichos"
    }

private val BudgetGroup.hint: String
    get() = when (this) {
        BudgetGroup.NEEDS -> "Casa, facturas, comida, transporte, salud y estudios"
        BudgetGroup.WANTS -> "Salir, ocio, suscripciones, compras y otros"
    }

/**
 * Tarjeta "Presupuestos" del mes, con la regla 50/30/20: Necesidades (50 %) y Caprichos (30 %) con
 * lo gastado frente a lo que les toca de la nómina (o de lo que ha entrado) y el Ahorro (20 %).
 * Cada grupo se abre para ver sus presupuestos; los que tienen uno justo o pasado salen abiertos.
 * Sin presupuestos, invita a crear el primero. Tocar un presupuesto lo abre para cambiarlo o quitarlo.
 */
@Composable
fun BudgetsCard(
    plan: BudgetPlan,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onOpen: (Budget) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val hasBudgets = plan.groups.any { it.statuses.isNotEmpty() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "🎯 Presupuestos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    plan.base?.let { base ->
                        Text(
                            "Reparto 50/30/20 de ${if (base.isSalary) "tu nómina" else "lo que ha entrado"} (${formatEuros(base.cents)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (hasBudgets && canAddMore) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Sin nada que comparar (ni presupuestos ni ingresos), solo la invitación
            if (plan.base != null || hasBudgets) {
                Spacer(Modifier.height(4.dp))
                plan.groups.forEach { group -> BudgetGroupSection(group, onOpen) }
                plan.savings?.let { SavingsGoalRow(it) }
            }

            if (!hasBudgets) {
                Text(
                    "Pon un límite a lo que gastas cada mes por categoría y te aviso al llegar al $BUDGET_WARNING_PERCENT %.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                OutlinedButton(
                    onClick = onAdd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Crear presupuesto", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Un grupo: su nombre y %, lo gastado frente a lo que le toca y, al tocarlo, sus presupuestos.
 * Empieza abierto si alguno va justo o pasado (lo que hay que mirar); si no, cerrado.
 */
@Composable
private fun BudgetGroupSection(summary: BudgetGroupSummary, onOpen: (Budget) -> Unit) {
    var expanded by rememberSaveable(summary.group) { mutableStateOf(summary.needsAttention) }
    val levelColor = summary.level.color
    val canExpand = summary.statuses.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${summary.group.title} · ${summary.group.percent} %",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (canExpand) {
                Text(
                    if (summary.statuses.size == 1) "1 presupuesto" else "${summary.statuses.size} presupuestos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Ocultar presupuestos" else "Ver presupuestos",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(summary.group.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        val target = summary.targetCents
        if (target != null) {
            LinearProgressIndicator(
                progress = { ((summary.percent ?: 0) / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = levelColor,
                trackColor = levelColor.copy(alpha = 0.15f),
                drawStopIndicator = {}
            )
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
    AnimatedVisibility(visible = expanded && canExpand) {
        Column(modifier = Modifier.padding(start = 12.dp)) {
            summary.statuses.forEach { status -> BudgetRow(status, onClick = { onOpen(status.budget) }) }
        }
    }
}

/** El 20 % que debería quedar: lo ahorrado (ingresos − gastos) frente a la meta. */
@Composable
private fun SavingsGoalRow(goal: SavingsGoal) {
    val color = if (goal.savedCents < 0) Rose500 else Emerald500
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(
            "🐷 Ahorro · $SAVINGS_PERCENT %",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text("Lo que te queda de lo que ha entrado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (goal.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            drawStopIndicator = {}
        )
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


/** Una categoría: icono, nombre, "120,00 € de 300,00 €", la barra y lo que queda (o lo pasado). */
@Composable
private fun BudgetRow(status: BudgetStatus, onClick: () -> Unit) {
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
            LinearProgressIndicator(
                progress = { (status.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = levelColor,
                trackColor = levelColor.copy(alpha = 0.15f),
                drawStopIndicator = {}
            )
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
    onDelete: (MovementCategory) -> Unit
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
        BudgetContent(existing, available, onSave, onDelete)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetContent(
    existing: Budget?,
    available: List<MovementCategory>,
    onSave: (MovementCategory, Long) -> Unit,
    onDelete: (MovementCategory) -> Unit
) {
    var amountText by remember { mutableStateOf(existing?.limitCents?.toAmountInput().orEmpty()) }
    var category by remember { mutableStateOf(existing?.category) }
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
                    // Por grupos del 50/30/20, como en la tarjeta: la lista se lee mejor
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
            } else {
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

/** Categorías de gasto que aún no tienen presupuesto (las que se pueden elegir al crear uno). */
fun categoriesWithoutBudget(budgets: List<Budget>): List<MovementCategory> =
    MovementCategory.of(MovementType.EXPENSE).filter { category -> budgets.none { it.category == category } }
