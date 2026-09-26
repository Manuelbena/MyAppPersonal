package com.syncro.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test de arquitectura: las pantallas y ViewModels hablan con los casos de uso, nunca con los
 * repositorios ni con Room. Así la lógica de negocio (validaciones, ids, sincronización) no se
 * duplica en la UI, que es justo lo que pasó con las notas.
 *
 * Excepciones permitidas por diseño (no son repositorios): data.auth.GoogleSignInClient (selector
 * de cuentas, UI del sistema) y data.preferences.ThemePreferences (ajuste de tema).
 */
class PresentationLayerDependenciesTest {

    private val presentationDir = File("src/main/java/com/syncro/presentation")

    private val forbiddenImports = Regex(
        """^import com\.syncro\.(domain\.repository|data\.repository|data\.local)\.""",
        RegexOption.MULTILINE
    )

    @Test
    fun `la presentacion no depende de repositorios ni de Room`() {
        assertTrue("No se encuentra ${presentationDir.absolutePath}", presentationDir.isDirectory)

        val violations = presentationDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                forbiddenImports.findAll(file.readText()).map {
                    "${file.relativeTo(presentationDir).invariantSeparatorsPath}: ${it.value}…"
                }
            }
            .toList()

        assertEquals("Imports prohibidos en presentation:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }
}
