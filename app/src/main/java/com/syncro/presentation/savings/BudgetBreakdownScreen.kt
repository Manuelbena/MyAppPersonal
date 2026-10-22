package com.syncro.presentation.savings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.cumulativeSpending
import com.syncro.domain.model.expenseShares
import com.syncro.domain.model.spendingLimitCents
import com.syncro.domain.model.BUDGET_WARNING_PERCENT
import com.syncro.domain.model.Budget
import com.syncro.domain.model.BudgetGroupSummary
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.budgetGroup
import com.syncro.domain.model.budgetPlan
import com.syncro.domain.model.totalsByCategory
import com.syncro.presentation.components.EdgeFades
import com.syncro.presentation.components.SyncroIconButton
import java.time.LocalDate

/**
 * El desglose de presupuestos, que se abre desde "Ver desglose" en Ahorros. Comparte el
 * [SavingsViewModel] de Ahorros, así que enseña el mismo mes y la misma cuenta.
 */
@Composable
fun BudgetBreakdownScreen(onBack: () -> Unit, viewModel: SavingsViewModel) {
    val movements by viewModel.state.collectAsState()
    val period by viewModel.currentPeriod.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    // Se lee al entrar: basta para marcar como "Previsto" lo que aún no ha llegado
    val today = remember(viewModel) { viewModel.today }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short) }
    }

    BudgetBreakdownContent(
        period = period,
        movements = movements,
        budgets = budgets,
        today = today,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSaveBudget = viewModel::saveBudget,
        onDeleteBudget = viewModel::deleteBudget,
        snackbarHostState = snackbarHostState
    )
}

/** Hoja abierta: un presupuesto existente, o null para crear uno (con la categoría ya elegida si se sabe). */
private data class BudgetTarget(val budget: Budget?, val category: MovementCategory? = null)

/**
 * El 50/30/20 del mes en detalle: la pila con su leyenda y, por grupo, cómo va frente a lo que le
 * toca, sus presupuestos y las categorías con gastos que aún no tienen límite (tocarlas lo pone).
 * Tocar una categoría abre su hoja: el límite y los movimientos del mes en ella.
 * Al final, el ahorro y el botón para añadir un presupuesto.
 */
@Composable
fun BudgetBreakdownContent(
    period: SavingsPeriod?,
    movements: MonthMovements?,
    budgets: List<Budget>,
    today: LocalDate,
    onBack: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSaveBudget: (MovementCategory, Long) -> Unit,
    onDeleteBudget: (MovementCategory) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var sheet by remember { mutableStateOf<BudgetTarget?>(null) }
    val available = categoriesWithoutBudget(budgets)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            BreakdownHeader(period, onBack, onPreviousMonth, onNextMonth)
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                ) {
                    if (movements != null) {
                        val plan = movements.budgetPlan(budgets)
                        val spentByCategory = movements.totalsByCategory(MovementType.EXPENSE).associate { it.category to it.cents }

                        item(key = "stack") {
                            BreakdownCard {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        plan.baseLabel() ?: "Este mes aún no ha entrado dinero: solo ves lo gastado.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    SpendingDonut(plan, modifier = Modifier.align(Alignment.CenterHorizontally))
                                    Spacer(Modifier.height(16.dp))
                                    BudgetStackLegend(plan)
                                }
                            }
                        }

                        item(key = "pace") {
                            BreakdownCard {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    ChartTitle("Ritmo del mes", "Lo gastado día a día; discontinuo, lo previsto que aún no ha llegado")
                                    Spacer(Modifier.height(12.dp))
                                    SpendingPaceChart(
                                        points = movements.cumulativeSpending(),
                                        limitCents = plan.spendingLimitCents,
                                        today = today
                                    )
                                }
                            }
                        }

                        val shares = movements.expenseShares()
                        if (shares.isNotEmpty()) {
                            item(key = "shares") {
                                BreakdownCard {
                                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                                        ChartTitle("¿En qué se va?", "Toca una categoría para ponerle límite", Modifier.padding(horizontal = 20.dp))
                                        Spacer(Modifier.height(8.dp))
                                        ExpenseShareBars(shares) { category ->
                                            sheet = budgets.firstOrNull { it.category == category }
                                                ?.let { BudgetTarget(it) }
                                                ?: BudgetTarget(null, category)
                                        }
                                    }
                                }
                            }
                        }

                        plan.groups.forEach { group ->
                            item(key = "group-${group.group}") {
                                GroupCard(
                                    summary = group,
                                    spentByCategory = spentByCategory,
                                    onOpenBudget = { sheet = BudgetTarget(it) },
                                    onCreateBudget = { sheet = BudgetTarget(null, it) }
                                )
                            }
                        }

                        plan.savings?.let { goal ->
                            item(key = "savings") {
                                BreakdownCard { Column(modifier = Modifier.padding(vertical = 10.dp)) { SavingsGoalRow(goal) } }
                            }
                        }

                        if (available.isNotEmpty()) {
                            item(key = "add") {
                                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                                    if (budgets.isEmpty()) {
                                        Text(
                                            "Pon un límite a lo que gastas cada mes por categoría y te aviso al llegar al $BUDGET_WARNING_PERCENT %.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                    }
                                    val accent = MaterialTheme.colorScheme.primary
                                    OutlinedButton(
                                        onClick = { sheet = BudgetTarget(null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (budgets.isEmpty()) "Crear presupuesto" else "Añadir presupuesto", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                EdgeFades(bottomHeight = 24.dp)
            }
        }
    }

    sheet?.let { target ->
        BudgetSheet(
            existing = target.budget,
            available = available,
            initialCategory = target.category,
            expenses = movements?.occurrences.orEmpty(),
            today = today,
            onDismiss = { sheet = null },
            onSave = { category, limitCents ->
                sheet = null
                onSaveBudget(category, limitCents)
            },
            onDelete = { category ->
                sheet = null
                onDeleteBudget(category)
            }
        )
    }
}

/** Volver, el título y el mes, con flechas como en Ahorros. */
@Composable
private fun BreakdownHeader(period: SavingsPeriod?, onBack: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SyncroIconButton(icon = Icons.AutoMirrored.Rounded.ArrowBack, onClick = onBack, contentDescription = "Volver")
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Presupuestos",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(period?.title().orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SyncroIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft, onClick = onPrevious, contentDescription = "Mes anterior")
            SyncroIconButton(icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight, onClick = onNext, contentDescription = "Mes siguiente")
        }
    }
}

@Composable
private fun BreakdownCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        content = content
    )
}

/** Un grupo: cómo va, sus presupuestos (los más apurados primero) y sus categorías con gastos sin límite. */
@Composable
private fun GroupCard(
    summary: BudgetGroupSummary,
    spentByCategory: Map<MovementCategory, Long>,
    onOpenBudget: (Budget) -> Unit,
    onCreateBudget: (MovementCategory) -> Unit
) {
    val unbudgeted = spentByCategory
        .filterKeys { category -> category.budgetGroup == summary.group && summary.statuses.none { it.budget.category == category } }
        .toList()
        .sortedByDescending { it.second }
    BreakdownCard {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            BudgetGroupHeader(summary)
            if (summary.statuses.isNotEmpty() || unbudgeted.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )
            }
            summary.statuses.forEach { status -> BudgetRow(status, onClick = { onOpenBudget(status.budget) }) }
            unbudgeted.forEach { (category, cents) -> UnbudgetedRow(category, cents, onClick = { onCreateBudget(category) }) }
        }
    }
}

/** Título de una gráfica y, debajo, cómo leerla. */
@Composable
private fun ChartTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
