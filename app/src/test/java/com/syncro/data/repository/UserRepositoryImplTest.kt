package com.syncro.data.repository

import com.syncro.data.local.SyncroDatabase
import com.syncro.domain.model.User
import com.syncro.testutil.createInMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Plan de pruebas de [UserRepositoryImpl]
 *
 * Responsabilidades: guardar el usuario con sesión iniciada, exponerlo y borrarlo.
 * `signInWithGoogle` queda fuera: depende de Credential Manager y de una cuenta real,
 * así que se prueba manualmente o con un test instrumentado.
 *
 * Riesgo principal: que la app use una cuenta distinta a la última con la que se inició
 * sesión, porque toda la sincronización con Google se hace con ese email.
 */
@RunWith(RobolectricTestRunner::class)
class UserRepositoryImplTest {

    private lateinit var db: SyncroDatabase
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = UserRepositoryImpl(db.userDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `sin sesion iniciada no hay usuario`() = runTest {
        assertNull(repository.getUser().first())
    }

    @Test
    fun `el usuario guardado se puede leer`() = runTest {
        repository.saveUser(ana)

        assertEquals(ana, repository.getUser().first())
    }

    @Test
    fun `cerrar sesion borra el usuario`() = runTest {
        repository.saveUser(ana)

        repository.clearUser()

        assertNull(repository.getUser().first())
    }

    @Test
    fun `iniciar sesion con otra cuenta sustituye a la anterior`() = runTest {
        repository.saveUser(ana)

        repository.saveUser(luis)

        assertEquals(luis, repository.getUser().first())
    }

    private companion object {
        val ana = User(email = "ana@example.com", name = "Ana", photoUrl = null, idToken = "token-ana")
        val luis = User(email = "luis@example.com", name = "Luis", photoUrl = null, idToken = "token-luis")
    }
}
