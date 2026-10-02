package com.syncro.presentation.savings

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
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/**
 * Tarjeta "Presupuestos" del mes: una barra por categoría con lo gastado frente al límite. Sin
 * presupuestos, invita a crear el primero. Tocar uno lo abre para cambiarlo o quitarlo.
 */
@Composable
fun BudgetsCard(
    statuses: List<BudgetStatus>,
    canAddMore: Boolean,
    onAdd: () -> Unit,
    onOpen: (Budget) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
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
                Text(
                    "🎯 Presupuestos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (statuses.isNotEmpty() && canAddMore) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Añadir", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (statuses.isEmpty()) {
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
            } else {
                Spacer(Modifier.height(4.dp))
                statuses.forEach { status -> BudgetRow(status, onClick = { onOpen(status.budget) }) }
            }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        available.forEach { option ->
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
