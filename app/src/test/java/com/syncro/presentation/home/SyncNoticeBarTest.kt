package com.syncro.presentation.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.syncro.domain.model.SyncState
import com.syncro.presentation.home.components.SyncNoticeBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan de pruebas del aviso de sincronización de Inicio: qué aviso toca según el estado
 * ([syncNoticeFor]) y qué ve y puede hacer el usuario ([SyncNoticeBar]).
 *
 * Riesgos: avisar de un fallo cuando el problema real es la falta de conexión, ofrecer
 * "Reintentar" sin red (no serviría de nada) y textos mal en singular/plural.
 */
@RunWith(RobolectricTestRunner::class)
class SyncNoticeBarTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sin conexion manda sobre el fallo y los pendientes`() {
        assertEquals(SyncNotice.Offline(3), syncNoticeFor(SyncState(isOnline = false, pendingChanges = 3), lastSyncFailed = true))
    }

    @Test
    fun `con conexion el fallo de sync va antes que los pendientes`() {
        assertEquals(SyncNotice.SyncFailed, syncNoticeFor(SyncState(isOnline = true, pendingChanges = 3), lastSyncFailed = true))
        assertEquals(SyncNotice.PendingChanges(3), syncNoticeFor(SyncState(isOnline = true, pendingChanges = 3), lastSyncFailed = false))
        assertNull(syncNoticeFor(SyncState(isOnline = true, pendingChanges = 0), lastSyncFailed = false))
    }

    @Test
    fun `sin conexion explica que los cambios se subiran solos y no ofrece reintentar`() {
        compose.setContent { SyncNoticeBar(notice = SyncNotice.Offline(1), onRetry = {}) }

        compose.onNodeWithText("Sin conexión · 1 cambio se subirá a Google al volver la red").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test
    fun `si fallo la sincronizacion se puede reintentar`() {
        var retries = 0
        compose.setContent { SyncNoticeBar(notice = SyncNotice.SyncFailed, onRetry = { retries++ }) }

        compose.onNodeWithText("No se pudo sincronizar con Google").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun `los cambios pendientes van en plural`() {
        compose.setContent { SyncNoticeBar(notice = SyncNotice.PendingChanges(4), onRetry = {}) }

        compose.onNodeWithText("4 cambios sin subir a Google").assertIsDisplayed()
    }
}
