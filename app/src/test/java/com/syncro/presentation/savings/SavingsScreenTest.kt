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

    private val savedBudgets = mutableListOf<String>()
    private val deletedBudgets = mutableListOf<MovementCategory>()

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
                onSaveBudget = { category, cents -> savedBudgets += "$category $cents" },
                onDeleteBudget = { deletedBudgets += it }
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
    fun `sin presupuestos invita a crear uno y se guarda con categoria e importe`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Crear presupuesto"))
        compose.onNodeWithText("Crear presupuesto").performClick()
        compose.onNodeWithText("Nuevo presupuesto").assertIsDisplayed()
        compose.onNodeWithText("Guardar").assertIsNotEnabled()
        compose.onNodeWithText("0,00 €").performTextInput("300")
        compose.onNodeWithText("Supermercado").performClick()
        compose.onNodeWithText("Guardar").assertIsEnabled().performClick()

        assertEquals(listOf("GROCERIES 30000"), savedBudgets)
    }

    @Test
    fun `un presupuesto muestra lo gastado y lo que queda, y se puede quitar`() {
        show(
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 15_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1), note = "Mercadona"))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        // Va bien (50 %), así que su grupo sale cerrado: se abre como lo haría el usuario
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Ver presupuestos"))
        compose.onNodeWithContentDescription("Ver presupuestos").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("150,00 € de 300,00 €"))
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
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 32_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1), note = "Mercadona"))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Te has pasado 20,00 €"))
        compose.onNodeWithText("106 %").assertIsDisplayed()
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

    // region Presupuestos agrupados (50/30/20)

    @Test
    fun `los presupuestos se agrupan en necesidades, caprichos y ahorro segun la nomina`() {
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

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Reparto 50/30/20 de tu nómina (2.000,00 €)"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("🏠 Necesidades · 50 %"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("800,00 € de 1.000,00 €"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("🐷 Ahorro · 20 %"))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("¡Objetivo cumplido! 💪"))
        // Va bien: el presupuesto de Vivienda queda dentro del grupo cerrado
        compose.onAllNodesWithText("800,00 € de 1.200,00 €").assertCountEquals(0)
    }

    @Test
    fun `un grupo con un presupuesto pasado sale abierto`() {
        show(
            monthMovements(october, listOf(aMovement(id = "m", amountCents = 32_000, category = MovementCategory.GROCERIES, date = LocalDate.of(2026, 10, 1)))),
            budgets = listOf(Budget(MovementCategory.GROCERIES, 30_000))
        )

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("320,00 € de 300,00 €"))
        compose.onNodeWithText("Te has pasado 20,00 €").assertIsDisplayed()
    }

    @Test
    fun `al crear un presupuesto las categorias salen por grupos`() {
        show()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Crear presupuesto"))
        compose.onNodeWithText("Crear presupuesto").performClick()

        compose.onNodeWithText("🏠 Necesidades · 50 %").assertIsDisplayed()
        compose.onNodeWithText("🎉 Caprichos · 30 %").performScrollTo().assertIsDisplayed()
    }

    // endregion
}
