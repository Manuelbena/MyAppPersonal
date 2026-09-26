package com.syncro.domain.usecase

import com.syncro.domain.model.User
import com.syncro.testutil.FakeUserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SignInWithGoogleUseCase] valida la cuenta elegida en el selector de Google y la guarda.
 *
 * Riesgo: guardar un email inválido. Toda la sincronización usa ese email como cuenta de Google,
 * así que con uno inválido la app quedaría "con sesión" pero sin poder sincronizar nada.
 */
class SignInWithGoogleUseCaseTest {

    private lateinit var users: FakeUserRepository
    private lateinit var signIn: SignInWithGoogleUseCase

    @Before
    fun setUp() {
        users = FakeUserRepository()
        signIn = SignInWithGoogleUseCase(users)
    }

    @Test
    fun `una cuenta valida se guarda como usuario actual`() = runTest {
        val result = signIn(account("ana@example.com"))

        assertEquals("ana@example.com", result.getOrNull()?.email)
        assertEquals("ana@example.com", users.getUser().first()?.email)
    }

    @Test
    fun `los espacios alrededor del email se eliminan`() = runTest {
        signIn(account("  ana@example.com  "))

        assertEquals("ana@example.com", users.getUser().first()?.email)
    }

    // Partición de emails inválidos: vacío, solo espacios, el texto "null" que a veces devuelve
    // la librería de Google y un texto sin @

    @Test
    fun `email vacio se rechaza`() = runTest { assertRejected("") }

    @Test
    fun `email con solo espacios se rechaza`() = runTest { assertRejected("   ") }

    @Test
    fun `email con el texto null se rechaza`() = runTest { assertRejected("null") }

    @Test
    fun `email sin arroba se rechaza`() = runTest { assertRejected("ana.example.com") }

    @Test
    fun `una cuenta invalida no sustituye a la sesion existente`() = runTest {
        signIn(account("ana@example.com"))

        signIn(account(""))

        assertEquals("ana@example.com", users.getUser().first()?.email)
    }

    private suspend fun assertRejected(email: String) {
        val result = signIn(account(email))

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertNull("No debe guardarse ningún usuario", users.getUser().first())
    }

    private fun account(email: String) = User(email = email, name = "Ana", photoUrl = null, idToken = "token")
}
