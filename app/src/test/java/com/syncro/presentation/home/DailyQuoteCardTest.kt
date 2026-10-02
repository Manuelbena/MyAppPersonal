package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncro.presentation.home.components.DailyQuoteCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** La tarjeta de la frase del día: su título, la frase con su autor y la ✕ para ocultarla. */
@RunWith(RobolectricTestRunner::class)
class DailyQuoteCardTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `muestra la frase del dia y la cruz la cierra`() {
        var closed = 0
        compose.setContent { DailyQuoteCard(quote = "Paso a paso", author = "Anónimo", onClose = { closed++ }) }

        compose.onNodeWithText("Frase del día").assertIsDisplayed()
        compose.onNodeWithText("\"Paso a paso\"").assertIsDisplayed()
        compose.onNodeWithText("— Anónimo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ocultar la frase hasta mañana").performClick()

        assertEquals(1, closed)
    }
}
