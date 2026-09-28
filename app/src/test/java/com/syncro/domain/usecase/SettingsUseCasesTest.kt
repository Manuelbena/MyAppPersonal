package com.syncro.domain.usecase

import com.syncro.domain.model.AppSettings
import com.syncro.domain.model.DataLossSummary
import com.syncro.domain.model.InvalidDigestTimesException
import com.syncro.domain.model.User
import com.syncro.testutil.CallLog
import com.syncro.testutil.FakeAccountDataRepository
import com.syncro.testutil.FakeSettingsRepository
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ajustes y cierre de sesión.
 * Responsabilidades: guardar los cambios sobre los ajustes actuales, rechazar un aviso de la mañana
 * que no sea antes que el de la noche, informar de lo que se perdería y cerrar sesión borrando
 * antes los datos. Riesgos: pisar un ajuste al cambiar otro, horas incoherentes y dejar datos de
 * una cuenta a la vista de la siguiente.
 */
class SettingsUseCasesTest {

    private val settings = FakeSettingsRepository()
    private val update = UpdateSettingsUseCase(settings)

    @Test
    fun `cambiar un ajuste conserva los demas`() = runTest {
        update { it.copy(digest = it.digest.copy(morningTime = at("08:30"))) }
        update { it.copy(assistant = it.assistant.copy(focusEnabled = false)) }

        val saved = settings.settings.first()
        assertEquals(at("08:30"), saved.digest.morningTime)
        assertEquals(false, saved.assistant.focusEnabled)
        assertEquals(AppSettings().digest.eveningTime, saved.digest.eveningTime)
    }

    @Test
    fun `la manana no puede ser a la misma hora o despues que la noche`() = runTest {
        val sameTime = update { it.copy(digest = it.digest.copy(morningTime = at("21:00"))) }
        val later = update { it.copy(digest = it.digest.copy(eveningTime = at("08:00"))) }

        assertTrue(sameTime.exceptionOrNull() is InvalidDigestTimesException)
        assertTrue(later.exceptionOrNull() is InvalidDigestTimesException)
        assertEquals("No debe guardarse nada", AppSettings(), settings.settings.first())
    }

    @Test
    fun `se informa de lo que se perderia al cerrar sesion`() = runTest {
        val accountData = FakeAccountDataRepository().apply { summary = DataLossSummary(unsyncedChanges = 2, notes = 3) }

        assertEquals(DataLossSummary(2, 3), GetDataLossSummaryUseCase(accountData)())
    }

    @Test
    fun `cerrar sesion borra los datos y despues la sesion`() = runTest {
        val log = CallLog()
        val users = object : com.syncro.domain.repository.UserRepository by FakeUserRepository() {
            override suspend fun clearUser() { log.calls += "clearUser" }
        }

        LogoutUseCase(FakeAccountDataRepository(log), users)()

        assertEquals(listOf("clearAccountData", "clearUser"), log.calls)
    }

    @Test
    fun `tras cerrar sesion no queda usuario`() = runTest {
        val users = FakeUserRepository().apply { saveUser(User("ana@example.com", "Ana", null)) }

        LogoutUseCase(FakeAccountDataRepository(), users)()

        assertNull(users.getUser().first())
    }
}
