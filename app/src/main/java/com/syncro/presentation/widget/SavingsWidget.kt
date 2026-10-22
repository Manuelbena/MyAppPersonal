package com.syncro.presentation.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.syncro.MainActivity
import com.syncro.domain.model.BudgetLevel
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.MovementType
import com.syncro.domain.usecase.ObserveSavingsSummaryUseCase
import com.syncro.presentation.home.components.balanceText
import com.syncro.presentation.home.components.budgetLine
import com.syncro.presentation.savings.formatEuros
import com.syncro.presentation.savings.title
import com.syncro.presentation.theme.Amber500
import com.syncro.presentation.theme.Emerald500
import com.syncro.presentation.theme.Rose500
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/*
 * Widget "Ahorros": cómo va el mes en la cuenta que se ve en Ahorros (balance, lo que entra y sale,
 * el presupuesto más apurado) y dos botones para apuntar un ingreso o un gasto sin buscar la
 * pestaña. Lee solo el móvil: los movimientos no salen de él.
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SavingsWidgetEntryPoint {
    fun observeSavingsSummary(): ObserveSavingsSummaryUseCase
    fun clock(): Clock
}

class SavingsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SavingsWidgetEntryPoint::class.java)
        val savings = entryPoint.observeSavingsSummary()(LocalDate.now(entryPoint.clock())).first()
        provideContent {
            GlanceTheme(colors = widgetColors()) {
                SavingsWidgetContent(savings)
            }
        }
    }
}

class SavingsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SavingsWidget()
}

@Composable
fun SavingsWidgetContent(savings: HomeSavings) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .widgetCorners()
            .padding(16.dp)
            .clickable(actionStartActivity(savingsIntent(context)))
    ) {
        Text(
            "💶 Ahorros",
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface)
        )
        Text(
            savings.period.title() + (savings.accountName?.let { " · $it" } ?: ""),
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(8.dp))

        if (!savings.hasMovements) {
            Text(
                "Aún no has apuntado nada este mes",
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 2
            )
        } else {
            Text(
                balanceText(savings.balanceCents),
                style = TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(if (savings.balanceCents >= 0) Emerald500 else Rose500)
                ),
                maxLines = 1
            )
            Text(
                "Entra ${formatEuros(savings.incomeCents)} · Sale ${formatEuros(savings.expenseCents)}",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1
            )
            // El presupuesto más apurado; los demás se ven en la app
            savings.tightBudgets.firstOrNull()?.let { status ->
                Text(
                    budgetLine(status),
                    modifier = GlanceModifier.padding(top = 4.dp),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(if (status.level == BudgetLevel.EXCEEDED) Rose500 else Amber500)
                    ),
                    maxLines = 1
                )
            }
        }

        // Los botones, siempre abajo
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            AddMovementButton("+ Ingreso", MovementType.INCOME, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            AddMovementButton("− Gasto", MovementType.EXPENSE, GlanceModifier.defaultWeight())
        }
    }
}

/** Abre Ahorros directamente en el formulario de ese tipo. */
@Composable
private fun AddMovementButton(label: String, type: MovementType, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(if (type == MovementType.INCOME) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
            .clickable(actionStartActivity(addMovementIntent(LocalContext.current, type))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (type == MovementType.INCOME) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
            ),
            maxLines = 1
        )
    }
}

/** Abre la app en Ahorros (ver MainActivity). */
private fun savingsIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
    .putExtra(MainActivity.EXTRA_OPEN_SAVINGS, true)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

/** Abre la app en Ahorros con el formulario de [type] (ver MainActivity). */
private fun addMovementIntent(context: Context, type: MovementType): Intent = Intent(context, MainActivity::class.java)
    .putExtra(MainActivity.EXTRA_ADD_MOVEMENT, type.name)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
