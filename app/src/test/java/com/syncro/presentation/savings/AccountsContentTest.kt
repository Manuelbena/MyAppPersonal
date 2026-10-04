package com.syncro.presentation.savings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.monthMovements
import com.syncro.testutil.DAY
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

/**
 * Cuentas de ahorro en pantalla: la de la cabecera de Ahorros, la lista para cambiar de cuenta y
 * crear una nueva.
 */
@RunWith(RobolectricTestRunner::class)
class AccountsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val principal = SavingsAccount(MAIN_ACCOUNT_ID, "Principal", ArgbColor(0xFF10B981))
    private val conjunta = SavingsAccount("conjunta", "Conjunta", ArgbColor(0xFF0EA5E9))

    private var selected: String? = null
    private var saved: Triple<String, ArgbColor, String?>? = null

    private fun showList(accounts: SavingsAccounts) {
        compose.setContent {
            AccountsContent(
                accounts = accounts,
                onSelect = { selected = it },
                onSave = { name, color, id -> saved = Triple(name, color, id) },
                onDelete = {}
            )
        }
    }

    @Test
    fun `la lista marca la que se ve y tocar otra la elige`() {
        showList(SavingsAccounts(listOf(principal, conjunta), active = principal))

        compose.onNodeWithText("Principal").assertIsDisplayed()
        compose.onNodeWithText("Conjunta").performClick()

        assertEquals("conjunta", selected)
    }

    @Test
    fun `nueva cuenta pide un nombre y la crea`() {
        showList(SavingsAccounts(listOf(principal), active = principal))

        compose.onNodeWithText("Nueva cuenta").performClick()
        compose.onNodeWithText("Nombre").performTextInput("Vacaciones")
        compose.onNodeWithText("Guardar").performClick()

        assertEquals("Vacaciones", saved?.first)
        assertEquals(null, saved?.third)
    }

    @Test
    fun `con una sola cuenta no se ofrece borrarla`() {
        showList(SavingsAccounts(listOf(principal), active = principal))

        compose.onNodeWithContentDescription("Cambiar Principal").performClick()

        compose.onNodeWithText("Cambiar cuenta").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTextCount("Eliminar cuenta"))
    }

    @Test
    fun `la cabecera de Ahorros dice la cuenta que se ve y abre la lista`() {
        compose.setContent {
            SavingsContent(
                period = SavingsPeriod.of(YearMonth.from(DAY)),
                movements = monthMovements(YearMonth.from(DAY), emptyList()),
                today = DAY,
                onPreviousMonth = {},
                onNextMonth = {},
                onSave = { _, _, _, _, _, _, _ -> },
                onDelete = {},
                accounts = SavingsAccounts(listOf(principal, conjunta), active = conjunta)
            )
        }

        compose.onNodeWithContentDescription("Cuenta Conjunta. Cambiar de cuenta").performClick()

        compose.onNodeWithText("Tus cuentas").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
