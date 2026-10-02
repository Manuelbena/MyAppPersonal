package com.syncro.presentation.savings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
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
    private val october = YearMonth.of(2026, 10)
    private val saved = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private fun show(movements: MonthMovements = monthMovements(october, emptyList())) {
        compose.setContent {
            SavingsContent(
                month = october,
                movements = movements,
                today = today,
                onPreviousMonth = {},
                onNextMonth = {},
                onSave = { type, cents, category, date, note, repeats -> saved += "$type $cents $category $date '$note' $repeats" },
                onDelete = { deleted += it }
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
    fun `borrar un movimiento mensual avisa de que se quita de todos los meses`() {
        show(monthMovements(october, listOf(aMovement(id = "netflix", amountCents = 1_299, category = MovementCategory.SUBSCRIPTIONS, date = LocalDate.of(2026, 10, 1), note = "Netflix", repeatsMonthly = true))))

        compose.onNodeWithText("Netflix").performClick()
        compose.onNodeWithText("se quitará de todos los meses", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Borrar").performClick()

        assertEquals(listOf("netflix"), deleted)
    }
}
