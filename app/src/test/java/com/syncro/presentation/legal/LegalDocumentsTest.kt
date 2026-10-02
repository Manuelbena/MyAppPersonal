package com.syncro.presentation.legal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Textos legales.
 * Responsabilidades: que cada documento tenga contenido, que la política de privacidad incluya lo
 * que exigen el RGPD y Google (responsable, derechos, reclamación ante la AEPD, cómo retirar el
 * acceso a Google, cláusula de Uso limitado) y que toda licencia citada esté incluida en la app.
 * Riesgos: citar una licencia cuyo texto no se distribuye (incumpliría Apache/BSD/MIT/OFL) o
 * borrar sin querer una cláusula obligatoria al editar los textos.
 */
@RunWith(RobolectricTestRunner::class)
class LegalDocumentsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun LegalDocument.fullText() = (listOf(intro) + sections.flatMap { listOf(it.heading) + it.paragraphs }).joinToString("\n")

    @Test
    fun `todos los documentos tienen contenido`() {
        LegalDocumentId.entries.forEach { id ->
            assertTrue("${id.title} sin introducción", legalDocument(id).intro.isNotBlank())
        }
        listOf(LegalDocumentId.PRIVACY, LegalDocumentId.TERMS, LegalDocumentId.LEGAL_NOTICE).forEach { id ->
            assertTrue("${id.title} sin apartados", legalDocument(id).sections.isNotEmpty())
        }
    }

    @Test
    fun `la politica de privacidad incluye las clausulas obligatorias`() {
        val text = legalDocument(LegalDocumentId.PRIVACY).fullText()

        listOf(
            "Responsable del tratamiento",
            LegalInfo.CONTACT_EMAIL,
            "Uso limitado",
            "Google API Services User Data Policy",
            "myaccount.google.com/permissions",
            "www.aepd.es",
            "Base legal",
            LegalInfo.LAST_UPDATED
        ).forEach { required -> assertTrue("Falta \"$required\" en la política de privacidad", required in text) }
    }

    @Test
    fun `el aviso legal identifica al titular`() {
        val text = legalDocument(LegalDocumentId.LEGAL_NOTICE).fullText()

        listOf(LegalInfo.OWNER_NAME, LegalInfo.OWNER_ADDRESS, LegalInfo.CONTACT_EMAIL).forEach {
            assertTrue("Falta \"$it\" en el aviso legal", it in text)
        }
    }

    @Test
    fun `toda licencia citada va incluida en la app`() {
        openSourceComponents.map { it.licenseFile }.distinct().forEach { path ->
            val file = File("src/main/assets/$path")
            assertTrue("No se distribuye el texto de $path", file.isFile && file.length() > 0)
        }
    }

    @Test
    fun `en licencias de codigo abierto se puede leer cada licencia completa`() {
        compose.setContent { LegalDocumentScreen(LegalDocumentId.OPEN_SOURCE, onBack = {}) }

        compose.onNodeWithText("Calendar (kizitonwose)").performScrollTo().performClick()

        compose.onNodeWithText("Permission is hereby granted", substring = true).assertIsDisplayed()
    }
}
