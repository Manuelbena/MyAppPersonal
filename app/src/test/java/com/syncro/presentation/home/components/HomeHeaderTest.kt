package com.syncro.presentation.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeHeaderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `un nombre de hasta seis letras se ve entero`() {
        assertEquals("Ana", greetingName("Ana"))
        assertEquals("Manuel", greetingName("Manuel"))
    }

    @Test
    fun `un nombre largo se corta a seis letras con puntos suspensivos`() {
        assertEquals("Alejan...", greetingName("Alejandro"))
    }

    // Regresión: un nombre largo llegaba hasta el botón de Ajustes y lo encogía
    @Test
    fun `un nombre largo no encoge el boton de ajustes`() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                HomeHeader(
                    userName = "Maximiliano",
                    userPhotoUrl = null,
                    currentDate = "Miércoles, 30 de septiembre de 2026",
                    onOpenSettings = {},
                    onTodayClick = {}
                )
            }
        }

        compose.onNodeWithText("Hola, Maximi...").assertExists()
        compose.onNodeWithContentDescription("Ajustes")
            .assertWidthIsEqualTo(44.dp)
            .assertHeightIsEqualTo(44.dp)
    }
}
