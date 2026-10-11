package com.syncro.presentation.savings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.Budget
import com.syncro.domain.model.MonthMovements
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
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
 * Ahorros en pantalla: el "+" lleva al formulario de ingreso o gasto, que solo deja guardar con
 * importe y categoría; el mes se resume con balance y tasa de ahorro, y borrar pide confirmación.
 */
@RunWith(RobolectricTestRunner::class)
class SavingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 10, 2)
    private val october = SavingsPeriod.of(YearMonth.of(2026, 10))
    private val saved = mutableListOf<String>()
    private val deleted = mutableListOf<String>()
    private val shared = mutableListOf<String>()
    private val sharedMonths = mutableListOf<String>()

    private var openedBudgets = 0

    private fun show(movements: MonthMovements = monthMovements(october, emptyList()), budgets: List<Budget> = emptyList()) {
        compose.setContent {
            SavingsContent(
                period = october,
                movements = movements,
                today = today,
                onPreviousMonth = {},
                onNextMonth = {},
                onSave = { type, cents, category, date, note, repeats, id -> saved += "$type $cents $category $date '$note' $repeats" + (id?.let { " id=$it" } ?: "") },
                onDelete = { deleted += it },
                onShare = { shared += it.movement.id },
                onShareMonth = { sharedMonths += it.month.toString() },
                budgets = budgets,
                onOpenBudgets = { openedBudgets++ }
            )
        }
    }

    @Test
    fun `el boton + ofrece nuevo ingreso y nuevo gasto`() {
        show()

        compose.onNodeWithContentDescription("Añadir movimiento").performClick()

        compose.onNodeWithText("Nuevo ingreso").assertIsDisplayed()
        compose.onNodeWithText("Nuevo gasto").assertIsDisplayed()
    }

    @Test
    fun `un gasto se guarda con importe, categoria y hoy`() {
        show()
        compose.onNodeWithContentDescription("Añadir movimiento").performClick()
        compose.onNodeWithText("Nuevo gasto").performClick()

        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("0,00 €").performTextInput("45,9")
        // Sin categoría aún no se puede guardar
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("Supermercado").performClick()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(listOf("EXPENSE 4590 GROCERIES 2026-10-02 '' false"), saved)
    }

    @Test
    fun `gastos e ingresos tienen la categoria Otros`() {
        show()
        compose.onNodeWithContentDescription("Añadir movimiento").performClick()
        compose.onNodeWithText("Nuevo ingreso").performClick()

        compose.onNodeWithText("Nómina").assertIsDisplayed()
        compose.onNodeWithText("Otros").assertIsDisplayed()
    }

    @Test
    fun `el resumen muestra balance, totales y tasa de ahorro`() {
        show(
            monthMovements(
                october,
                listOf(
                    aMovement(id = "n", type = MovementType.INCOME, amountCents = 200_000, category = MovementCategory.SALARY, date = LocalDate.of(2026, 10, 1), note = null),
                    aMovement(id = "a", amountCents = 150_000, category = MovementCategory.HOUSING, date = LocalDate.of(2026, 10, 1), note = "Alquiler")
                )
            )
        )

        // El formato pone un espacio no separable antes del € (no se parte en dos líneas)
        compose.onNodeWithText("+500,00 €").assertIsDisplayed()
        compose.onNodeWithText("2.000,00 €").assertIsDisplayed()
        compose.onNodeWithText("Ahorras el 25 %", substring = true).assertIsDisplayed()
        // La lista es perezosa: en la pantalla pequeña del test, la fila queda más abajo
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Alquiler"))
        compose.onNodeWithText("−1.500,00 €").assertIsDisplayed()
    }

    @Test
    fun `tocar un movimiento abre su detalle con importe, editar y compartir`() {
        show(monthMovements(october, listOf(netflix)))

        openNetflix()

        compose.onNodeWithText("Guardado solo en este móvil").assertExists()
        compose.onNodeWithText("Cada mes desde el", substring = true).assertExists()
        compose.onNodeWithText("Editar").assertIsDisplayed()
        compose.onNodeWithText("Compartir").performClick()

        assertEquals(listOf("netflix"), shared)
    }

    @Test
    fun `borrar desde el detalle avisa de que un mensual se quita de todos los meses`() {
        show(monthMovements(october, listOf(netflix)))

        openNetflix()
        compose.onNodeWithText("Eliminar gasto").performScrollTo().performClick()
        compose.onNodeWithText("se quitará de todos los meses", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Eliminar").performClick()

        assertEquals(listOf("netflix"), deleted)
    }

    @Test
    fun `editar abre el formulario relleno y guarda con el mismo id`() {
        show(monthMovements(october, listOf(netflix)))

        openNetflix()
        compose.onNodeWithText("Editar").performClick()

        compose.onNodeWithText("Editar gasto").assertIsDisplayed()
        compose.onNodeWithText("los cambios se aplican a todos los meses", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(listOf("EXPENSE 1299 SUBSCRIPTIONS 2026-10-01 'Netflix' true id=netflix"), saved)
    }

    @Test
    fun `apuntar nomina desde el asistente abre el ingreso con Nomina ya elegida`() {
        var opened = 0
        compose.setContent {
            SavingsContent(
                period = october,
                movements = monthMovements(october, emptyList()),
                today = today,
                onPreviousMonth = {},
                onNextMonth = {},
                onSave = { type, cents, category, date, _, _, _ -> saved += "$type $cents $category $date" },
                onDelete = {},
                openSalaryForm = true,
                onSalaryFormOpened = { opened++ }
            )
        }

        compose.onNodeWithText("Nuevo ingreso").assertIsDisplayed()
        compose.onNodeWithText("0,00 €").performTextInput("1850")
        // Con la categoría ya elegida basta con el importe
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(1, opened)
        assertEquals(listOf("INCOME 185000 SALARY 2026-10-02"), saved)
    }

    @Test
    fun `con movimientos se puede compartir el resumen del mes`() {
        show(monthMovements(october, listOf(netflix)))

        compose.onNodeWithText("Compartir resumen del mes").performClick()

        assertEquals(listOf("2026-10"), sharedMonths)
    }

    @Test
    fun `un mes sin movimientos no ofrece compartir el resumen`() {
        show()

        compose.onNodeWithText("Compartir resumen del mes").assertDoesNotExist()
    }

    /** La lista es perezosa y la pantalla del test es pequeña: se baja hasta la fila antes de tocarla. */
    private fun openNetflix() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Netflix"))
        compose.onNodeWithText("Netflix").performClick()
    }

    private val netflix = aMovement(
        id = "netflix", amountCents = 1_299, category = MovementCategory.SUBSCRIPTIONS,
        date = LocalDate.of(2026, 10, 1), note = "Netflix", repeatsMonthly = true
    )

    // region Tarjeta de presupuestos (compacta; el detalle está en BudgetBreakdownContentTest)

    @Test
    fun `sin datos la tarjeta invita a poner limites y ver desglose abre el detalle`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ver desglose"))
        compose.onNodeWithText("Pon un límite", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Ver desglose").performClick()

        assertEquals(1, openedBudgets)
    }

    @Test
    fun `la tarjeta resume el 50-30-20 en una barra con su leyenda`() {
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

        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Reparto del mes"))
        compose.onNodeWithText("Reparto 50/30/20 de tu nómina (2.000,00 €)").assertIsDisplayed()
        compose.onNodeWithText("Necesidades").assertIsDisplayed()
        compose.onNodeWithText("de 1.000,00 €").assertIsDisplayed()
        compose.onNodeWithText("Ahorro").assertIsDisplayed()
        compose.onNodeWithText("1.200,00 €").assertIsDisplayed()
        // Las categorías y sus presupuestos solo están en el desglose
        compose.onAllNodesWithText("800,00 € de 1.200,00 €").assertCountEquals(0)
    }

    @Test
    fun `la tarjeta avisa si un presupuesto se ha pasado`() {
        show(
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 32_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1)))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("⚠️ 1 presupuesto pasado"))
        compose.onNodeWithText("⚠️ 1 presupuesto pasado").assertIsDisplayed()
    }

    // endregion
}
