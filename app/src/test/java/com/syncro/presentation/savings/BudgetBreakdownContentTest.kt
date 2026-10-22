package com.syncro.presentation.savings

import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import com.syncro.domain.model.label
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.monthMovements
import com.syncro.testutil.aMovement
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

/**
 * El desglose de presupuestos ("Ver desglose" en Ahorros): cada grupo del 50/30/20 con lo gastado
 * frente a lo que le toca, sus presupuestos (lo que queda o lo pasado) y las categorías con gastos
 * sin límite, que se tocan para ponerle uno. Crear, cambiar y quitar presupuestos se hace aquí.
 */
@RunWith(RobolectricTestRunner::class)
class BudgetBreakdownContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val october = SavingsPeriod.of(YearMonth.of(2026, 10))
    private val savedBudgets = mutableListOf<String>()
    private val deletedBudgets = mutableListOf<MovementCategory>()
    private var back = 0

    private fun show(movements: MonthMovements = monthMovements(october, emptyList()), budgets: List<Budget> = emptyList()) {
        compose.setContent {
            BudgetBreakdownContent(
                period = october,
                movements = movements,
                budgets = budgets,
                today = LocalDate.of(2026, 10, 2),
                onBack = { back++ },
                onPreviousMonth = {},
                onNextMonth = {},
                onSaveBudget = { category, cents -> savedBudgets += "$category $cents" },
                onDeleteBudget = { deletedBudgets += it }
            )
        }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    @Test
    fun `volver llama a onBack`() {
        show()

        compose.onNodeWithContentDescription("Volver").performClick()

        assertEquals(1, back)
    }

    @Test
    fun `sin presupuestos invita a crear uno y se guarda con categoria e importe`() {
        show()

        scrollTo("Crear presupuesto")
        compose.onNodeWithText("Crear presupuesto").performClick()
        compose.onNodeWithText("Nuevo presupuesto").assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("0,00 €").performTextInput("300")
        compose.onNodeWithText("Supermercado").performClick()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(listOf("GROCERIES 30000"), savedBudgets)
    }

    @Test
    fun `al crear un presupuesto las categorias salen por grupos`() {
        show()

        scrollTo("Crear presupuesto")
        compose.onNodeWithText("Crear presupuesto").performClick()

        compose.onNodeWithText("Nuevo presupuesto").assertIsDisplayed()
        // Los grupos también salen en el desglose de detrás: se miran los de la hoja (los últimos)
        compose.onAllNodesWithText("🏠 Necesidades · 50 %").onLast().assertIsDisplayed()
        compose.onAllNodesWithText("🎉 Caprichos · 30 %").onLast().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `un presupuesto muestra lo gastado y lo que queda, y se puede quitar`() {
        show(
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 15_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1), note = "Mercadona"))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        scrollTo("150,00 € de 300,00 €")
        compose.onNodeWithText("Te quedan 150,00 €").assertIsDisplayed()
        compose.onNodeWithText("50 %").assertIsDisplayed()

        compose.onNodeWithText("Te quedan 150,00 €").performClick()
        compose.onNodeWithText("Presupuesto de Supermercado").assertIsDisplayed()
        compose.onNodeWithText("Quitar presupuesto").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()

        assertEquals(listOf(MovementCategory.GROCERIES), deletedBudgets)
    }

    @Test
    fun `pasarse del presupuesto se dice en rojo`() {
        show(
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 32_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1)))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        scrollTo("Te has pasado 20,00 €")
        compose.onNodeWithText("106 %").assertIsDisplayed()
    }

    @Test
    fun `los grupos comparan lo gastado con la nomina y el ahorro con el 20 %`() {
        show(
            monthMovements(
                october,
                listOf(
                    aMovement(id = "nomina", type = MovementType.INCOME, amountCents = 200_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 1)),
                    aMovement(id = "alquiler", amountCents = 80_000, category = MovementCategory.HOUSING, date = LocalDate.of(2026, 10, 1))
                )
            ),
            budgets = listOf(Budget(MovementCategory.HOUSING, 120_000))
        )

        scrollTo("Reparto 50/30/20 de tu nómina (2.000,00 €)")
        scrollTo("🏠 Necesidades · 50 %")
        scrollTo("800,00 € de 1.000,00 €")
        // El presupuesto se ve directamente, sin abrir nada
        scrollTo("800,00 € de 1.200,00 €")
        scrollTo("🐷 Ahorro · 20 %")
        scrollTo("¡Objetivo cumplido! 💪")
    }

    @Test
    fun `una categoria con gastos sin limite abre la hoja con esa categoria elegida`() {
        show(monthMovements(october, listOf(aMovement(id = "m", amountCents = 4_500, category = MovementCategory.RESTAURANTS, date = LocalDate.of(2026, 10, 1)))))

        scrollTo("Sin límite · Toca para poner uno")
        compose.onNodeWithText("Sin límite · Toca para poner uno").performClick()
        compose.onNodeWithText("0,00 €").performTextInput("100")
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(listOf("RESTAURANTS 10000"), savedBudgets)
    }

    @Test
    fun `tocar un presupuesto ensena los movimientos de su categoria con el total`() {
        show(
            monthMovements(
                october,
                listOf(
                    aMovement(id = "a", amountCents = 15_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1), note = "Mercadona"),
                    aMovement(id = "b", amountCents = 5_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 2), note = "Lidl"),
                    aMovement(id = "c", amountCents = 4_500, category = MovementCategory.RESTAURANTS, date = LocalDate.of(2026, 10, 1), note = "Pizzería")
                )
            ),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        scrollTo("200,00 € de 300,00 €")
        // La fila del presupuesto, en su grupo (el nombre también sale antes, en "¿En qué se va?")
        compose.onAllNodesWithText("Supermercado").onLast().performClick()

        compose.onNodeWithText("MOVIMIENTOS DEL MES").performScrollTo().assertIsDisplayed()
        // El total de la categoría junto al título
        compose.onAllNodesWithText("200,00 €").onLast().performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Mercadona").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lidl").performScrollTo().assertIsDisplayed()
        // Solo los de su categoría
        compose.onNodeWithText("Pizzería").assertDoesNotExist()
    }

    @Test
    fun `una categoria sin gastos lo dice en la hoja`() {
        show()

        scrollTo("Crear presupuesto")
        compose.onNodeWithText("Crear presupuesto").performClick()
        compose.onAllNodesWithText("Supermercado").onLast().performClick()

        compose.onNodeWithText("Este mes aún no hay gastos en Supermercado.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `las graficas resumen el mes y tocar una categoria abre su hoja`() {
        show(
            monthMovements(
                october,
                listOf(
                    aMovement(id = "a", amountCents = 15_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1), note = "Mercadona"),
                    aMovement(id = "c", amountCents = 4_500, category = MovementCategory.RESTAURANTS, date = LocalDate.of(2026, 10, 1), note = "Pizzería")
                )
            )
        )

        compose.onNodeWithContentDescription("Reparto del mes", substring = true).assertExists()
        scrollTo("Ritmo del mes")
        compose.onNodeWithText("Ritmo del mes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ritmo del mes", substring = true).assertExists()
        scrollTo("¿En qué se va?")
        compose.onNodeWithText("¿En qué se va?").assertIsDisplayed()
        scrollTo("76 %")
        compose.onNodeWithText("76 %").assertIsDisplayed()

        // Restaurantes no tiene presupuesto: su hoja deja crearlo y enseña sus gastos del mes
        compose.onAllNodesWithText(MovementCategory.RESTAURANTS.label).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Pizzería").performScrollTo().assertIsDisplayed()
    }
}
