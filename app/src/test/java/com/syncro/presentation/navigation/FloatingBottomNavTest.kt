package com.syncro.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** La barra de abajo cabe entera en cualquier ancho de móvil. */
@RunWith(RobolectricTestRunner::class)
class FloatingBottomNavTest {

    @get:Rule
    val compose = createComposeRule()

    // Regresión: con anchos fijos, en móviles de 360 dp o menos el Asistente se salía de la barra
    @Test
    fun `en una pantalla pequena se ven todas las pestanas`() {
        compose.setContent {
            Box(Modifier.width(320.dp)) {
                FloatingBottomNav(items = bottomNavItems, currentRoute = AppScreen.Home.route, onItemClick = {})
            }
        }

        bottomNavItems.forEach { screen ->
            val node = compose.onNodeWithContentDescription(screen.title).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("${screen.title} se sale de la barra", node.boundsInRoot.right <= with(compose.density) { 320.dp.toPx() })
        }
    }
}
