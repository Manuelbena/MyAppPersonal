package com.syncro.presentation.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Las páginas web de los textos legales (docs/legal), las que se publican para Google Play.
 * Riesgo: que la web diga una cosa y la app otra porque alguien cambió los textos y no regeneró
 * las páginas. Si este test falla tras cambiar LegalDocuments.kt, regenera con:
 *
 *     UPDATE_LEGAL_HTML=true ./gradlew testDebugUnitTest --tests "*LegalHtmlTest"
 */
class LegalHtmlTest {

    // Los tests de Gradle se ejecutan desde la carpeta del módulo (app/)
    private val folder = File("../docs/legal")

    @Test
    fun `las paginas publicadas coinciden con los textos de la app`() {
        val update = System.getenv("UPDATE_LEGAL_HTML") == "true"
        LegalDocumentId.entries.forEach { id ->
            val file = File(folder, id.fileName)
            val expected = legalDocumentHtml(id)
            if (update) {
                folder.mkdirs()
                file.writeText(expected)
            }
            assertTrue("Falta docs/legal/${id.fileName}: regenera con UPDATE_LEGAL_HTML=true", file.exists())
            assertEquals("docs/legal/${id.fileName} no está al día: regenera con UPDATE_LEGAL_HTML=true", expected, file.readText())
        }
    }

    @Test
    fun `la politica publicada incluye lo que Google exige`() {
        val html = legalDocumentHtml(LegalDocumentId.PRIVACY)

        listOf("Uso limitado", "Cómo borrar tus datos", "Permisos del móvil", "myaccount.google.com/permissions", LegalInfo.CONTACT_EMAIL.replace("\"", "&quot;"))
            .forEach { assertTrue("Falta \"$it\"", html.contains(it)) }
    }

    @Test
    fun `el html no rompe con comillas ni simbolos de los textos`() {
        val html = legalDocumentHtml(LegalDocumentId.TERMS)

        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(!html.contains("<script"))
    }
}
