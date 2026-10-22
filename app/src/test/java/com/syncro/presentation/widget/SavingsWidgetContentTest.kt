package com.syncro.presentation.widget

import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import com.syncro.domain.model.Budget
import com.syncro.domain.model.BudgetStatus
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.savingsPeriodOf
import com.syncro.presentation.savings.formatEuros
import com.syncro.testutil.DAY
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Contenido del widget "Ahorros": el mes, el balance con su signo, lo que entra y sale, el
 * presupuesto más apurado y los dos botones. Riesgos: mes sin movimientos, saldo negativo y
 * varias cuentas (hay que decir cuál se ve).
 */
@RunWith(RobolectricTestRunner::class)
class SavingsWidgetContentTest {

    private val period = savingsPeriodOf(DAY, paydayDay = null)

    private fun show(savings: HomeSavings, assertions: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { SavingsWidgetContent(savings) }
            assertions()
        }

    @Test
    fun `muestra el mes, el balance y lo que entra y sale`() = show(
        HomeSavings(period, incomeCents = 200_000, expenseCents = 76_550, hasMovements = true, tightBudgets = emptyList())
    ) {
        onNode(hasText("Septiembre 2026")).assertExists()
        onNode(hasText("+${formatEuros(123_450)}")).assertExists()
        onNode(hasText("Entra ${formatEuros(200_000)} · Sale ${formatEuros(76_550)}")).assertExists()
        onNode(hasText("+ Ingreso")).assertExists()
        onNode(hasText("− Gasto")).assertExists()
    }

    @Test
    fun `un mes en negativo lleva el signo menos y dice el presupuesto apurado`() = show(
        HomeSavings(
            period,
            incomeCents = 0,
            expenseCents = 4_590,
            hasMovements = true,
            tightBudgets = listOf(BudgetStatus(Budget(MovementCategory.GROCERIES, 5_000), spentCents = 4_590)),
            accountName = "Conjunta"
        )
    ) {
        onNode(hasText("Septiembre 2026 · Conjunta")).assertExists()
        onNode(hasText("−${formatEuros(4_590)}")).assertExists()
        onNode(hasText("⚠️ 🛒 Supermercado · 91 %")).assertExists()
    }

    @Test
    fun `sin movimientos invita a apuntar`() = show(
        HomeSavings(period, incomeCents = 0, expenseCents = 0, hasMovements = false, tightBudgets = emptyList())
    ) {
        onNode(hasText("Aún no has apuntado nada este mes")).assertExists()
        onNode(hasText("+ Ingreso")).assertExists()
    }
}
