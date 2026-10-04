package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.Budget
import com.syncro.domain.model.BudgetStatus
import com.syncro.domain.model.HomeSavings
import com.syncro.domain.model.MovementCategory
import com.syncro.presentation.home.components.HomeSavingsCard
import com.syncro.presentation.home.components.balanceText
import com.syncro.presentation.home.components.budgetLine
import com.syncro.presentation.savings.formatEuros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * Plan de pruebas de la tarjeta de Ahorros de Inicio: el balance con su signo, las líneas de
 * presupuesto y que lleve a Ahorros.
 *
 * Riesgos: un balance negativo mostrado como positivo, "te has pasado −12 €" (doble negativo) y
 * una tarjeta vacía sin explicar nada cuando aún no hay movimientos.
 */
@RunWith(RobolectricTestRunner::class)
class HomeSavingsCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val october = SavingsPeriod.of(YearMonth.of(2026, 10))

    @Test
    fun `el balance lleva signo segun el mes vaya bien o mal`() {
        assertEquals("+${formatEuros(123_000)}", balanceText(123_000))
        assertEquals("−${formatEuros(4_590)}", balanceText(-4_590))
    }

    @Test
    fun `un presupuesto justo dice el porcentaje y lo que queda, uno pasado cuanto sobra`() {
        val groceries = Budget(MovementCategory.GROCERIES, 30_000)

        val warning = budgetLine(BudgetStatus(groceries, spentCents = 25_500))
        val exceeded = budgetLine(BudgetStatus(groceries, spentCents = 31_200))

        assertTrue(warning, warning.endsWith("85 % · quedan ${formatEuros(4_500)}"))
        assertTrue(exceeded, exceeded.endsWith("te has pasado ${formatEuros(1_200)}"))
    }

    @Test
    fun `muestra el mes y el balance y al tocarla abre Ahorros`() {
        var opened = 0
        compose.setContent {
            HomeSavingsCard(
                HomeSavings(october, incomeCents = 150_000, expenseCents = 27_000, hasMovements = true, tightBudgets = emptyList()),
                onOpenSavings = { opened++ }
            )
        }

        compose.onNodeWithText("💶 Ahorros de octubre").assertIsDisplayed()
        compose.onNodeWithText(balanceText(123_000)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `sin movimientos invita a apuntar el primero`() {
        compose.setContent {
            HomeSavingsCard(HomeSavings(october, 0, 0, hasMovements = false, tightBudgets = emptyList()), onOpenSavings = {})
        }

        compose.onNodeWithText("Aún no has apuntado nada este mes. Toca para apuntar tu primer ingreso o gasto.").assertIsDisplayed()
    }

    @Test
    fun `con muchos presupuestos justos enseña dos y cuenta el resto`() {
        val tight = listOf(MovementCategory.GROCERIES, MovementCategory.LEISURE, MovementCategory.TRANSPORT, MovementCategory.HOUSING)
            .map { BudgetStatus(Budget(it, 10_000), spentCents = 9_000) }
        compose.setContent {
            HomeSavingsCard(HomeSavings(october, 0, 36_000, hasMovements = true, tightBudgets = tight), onOpenSavings = {})
        }

        compose.onNodeWithText("y 2 presupuestos más").assertIsDisplayed()
    }
}
