package com.syncro.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
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

    // Regresión: la botonera de tres botones (48 dp) tapaba la barra, cuyo margen es menor
    @Test
    fun `la barra queda por encima de la botonera del sistema`() {
        assertEquals(60.dp, bottomBarMargin(margin = 24.dp, systemBar = 48.dp))
        assertEquals(60.dp, bottomBarMargin(margin = 40.dp, systemBar = 48.dp))
    }

    @Test
    fun `con gestos la barra conserva su margen`() {
        assertEquals(40.dp, bottomBarMargin(margin = 40.dp, systemBar = 24.dp))
        assertEquals(40.dp, bottomBarMargin(margin = 40.dp, systemBar = 0.dp))
    }
}
