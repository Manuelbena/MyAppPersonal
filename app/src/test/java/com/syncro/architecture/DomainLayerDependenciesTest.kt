package com.syncro.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test de arquitectura: el dominio es Kotlin puro y no puede depender de Android ni de Compose.
 *
 * No prueba comportamiento sino una regla de diseño. Sin él, la regla solo existe mientras
 * alguien se acuerde de ella en cada revisión de código.
 */
class DomainLayerDependenciesTest {

    // Gradle ejecuta los tests unitarios con el directorio del módulo (app/) como directorio de trabajo
    private val domainDir = File("src/main/java/com/syncro/domain")

    // Referencias a android.* / androidx.*, tanto en imports como con nombre completo
    private val forbidden = Regex("""\bandroidx?\.[a-z]""")

    /**
     * Deuda técnica conocida. Cada entrada debe desaparecer cuando se arregle: el test avisa si
     * una excepción ya no hace falta, para que la lista no se quede desactualizada.
     */
    private val knownExceptions = setOf(
        "repository/UserRepository.kt", // signInWithGoogle(context): Credential Manager necesita un Context
        "usecase/SignInWithGoogleUseCase.kt"
    )

    @Test
    fun `el dominio no depende de Android ni de Compose`() {
        val violations = domainFiles()
            .filter { (path, _) -> path !in knownExceptions }
            .filter { (_, content) -> forbidden.containsMatchIn(content) }
            .map { (path, content) -> "$path -> ${forbidden.find(content)!!.value}…" }

        assertEquals("Dependencias de Android/Compose en domain:\n${violations.joinToString("\n")}", emptyList<String>(), violations)
    }

    @Test
    fun `las excepciones conocidas siguen siendo necesarias`() {
        val files = domainFiles().toMap()
        knownExceptions.forEach { path ->
            val content = files[path]
            assertTrue("$path ya no existe: quítalo de knownExceptions", content != null)
            assertTrue("$path ya no depende de Android: quítalo de knownExceptions", forbidden.containsMatchIn(content!!))
        }
    }

    private fun domainFiles(): List<Pair<String, String>> {
        assertTrue("No se encuentra ${domainDir.absolutePath}", domainDir.isDirectory)
        return domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(domainDir).invariantSeparatorsPath to it.readText() }
            .toList()
    }
}
