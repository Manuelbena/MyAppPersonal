package com.syncro.presentation.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.syncro.domain.model.BudgetLevel
import com.syncro.domain.model.BudgetStatus
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.emoji
import com.syncro.domain.model.label
import com.syncro.presentation.savings.formatEuros
import com.syncro.presentation.theme.Amber500
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.Rose500
import java.time.format.TextStyle
import java.util.Locale

/** Cuántos presupuestos justos se listan; del resto solo se dice cuántos hay. */
private const val MAX_BUDGET_LINES = 2

/**
 * El resumen de Ahorros en Inicio (si se activa en Ajustes > Asistente): el balance del mes, lo que
 * entra y sale, y los presupuestos que van justos o pasados. Toda la tarjeta lleva a Ahorros.
 */
@Composable
fun HomeSavingsCard(
    savings: HomeSavings,
    onOpenSavings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (savings.balanceCents >= 0) Emerald500 else Rose500
    val monthName = savings.month.month.getDisplayName(TextStyle.FULL, Locale("es", "ES"))

    Surface(
        onClick = onOpenSavings,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent)))
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "💶 Ahorros de $monthName",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!savings.hasMovements) {
                Text(
                    "Aún no has apuntado nada este mes. Toca para apuntar tu primer ingreso o gasto.",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    balanceText(savings.balanceCents),
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    "Entra ${formatEuros(savings.incomeCents)} · Sale ${formatEuros(savings.expenseCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (savings.tightBudgets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                savings.tightBudgets.take(MAX_BUDGET_LINES).forEach { status ->
                    Text(
                        budgetLine(status),
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (status.level == BudgetLevel.EXCEEDED) Rose500 else Amber500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val more = savings.tightBudgets.size - MAX_BUDGET_LINES
                if (more > 0) {
                    Text(
                        if (more == 1) "y 1 presupuesto más" else "y $more presupuestos más",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** "+1.234,56 €" o "−45,90 €": el signo dice si el mes va bien o mal. */
internal fun balanceText(cents: Long): String =
    if (cents >= 0) "+${formatEuros(cents)}" else "−${formatEuros(-cents)}"

/** "⚠️ 🛒 Supermercado · 85 % · quedan 45,00 €" o "⛔ 🎉 Ocio · te has pasado 12,00 €". */
internal fun budgetLine(status: BudgetStatus): String {
    val category = "${status.budget.category.emoji} ${status.budget.category.label}"
    return if (status.level == BudgetLevel.EXCEEDED) {
        "⛔ $category · te has pasado ${formatEuros(-status.remainingCents)}"
    } else {
        "⚠️ $category · ${status.percent} % · quedan ${formatEuros(status.remainingCents)}"
    }
}
