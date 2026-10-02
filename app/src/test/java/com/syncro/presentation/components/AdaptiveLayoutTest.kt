package com.syncro.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan: los cortes de ancho deciden el diseño de cada pantalla (móvil, tablet vertical, tablet
 * horizontal). Riesgos: un corte desplazado deja una tablet con el diseño de móvil, y un
 * [ReadableWidth] que no limita el ancho estira el texto de lado a lado.
 */
@RunWith(RobolectricTestRunner::class)
class AdaptiveLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `los cortes de ancho son los de Material`() {
        assertEquals(WidthClass.Compact, widthClassFor(411.dp))
        assertEquals(WidthClass.Compact, widthClassFor(599.dp))
        assertEquals(WidthClass.Medium, widthClassFor(600.dp))
        assertEquals(WidthClass.Medium, widthClassFor(800.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(840.dp))
        assertEquals(WidthClass.Expanded, widthClassFor(1280.dp))
    }

    @Test
    fun `en una pantalla ancha el contenido de lectura no pasa del ancho maximo`() {
        compose.setContent {
            Box(Modifier.requiredWidth(1280.dp)) {
                ReadableWidth {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                }
            }
        }

        compose.onNodeWithTag("contenido").assertWidthIsEqualTo(MaxReadableWidth)
    }

    @Test
    fun `en el movil el contenido de lectura ocupa todo el ancho`() {
        compose.setContent {
            Box(Modifier.requiredWidth(360.dp)) {
                ReadableWidth {
                    Box(Modifier.fillMaxSize().testTag("contenido"))
                }
            }
        }

        compose.onNodeWithTag("contenido").assertWidthIsEqualTo(360.dp)
    }
}
