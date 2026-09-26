package com.syncro.presentation

import com.syncro.domain.model.User
import com.syncro.domain.usecase.GetLocalUserUseCase
import com.syncro.testutil.FakeUserRepository
import com.syncro.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [AuthViewModel] decide si se abre el login o el inicio. Riesgo: confundir "todavía no lo sé"
 * con "no hay sesión", que provocaba ver el login un instante al abrir la app con sesión iniciada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `mientras no se ha leido la sesion el estado es Cargando, no Sin sesion`() = runTest {
        // Un repositorio que aún no ha emitido nada, como Room justo al arrancar
        val slowUsers = object : com.syncro.domain.repository.UserRepository {
            override fun getUser(): Flow<User?> = emptyFlow()
            override suspend fun saveUser(user: User) = Unit
            override suspend fun clearUser() = Unit
        }
        val viewModel = AuthViewModel(GetLocalUserUseCase(slowUsers))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect() }

        assertEquals(SessionState.Loading, viewModel.session.value)
    }

    @Test
    fun `sin usuario guardado el estado es Sin sesion`() = runTest {
        val viewModel = AuthViewModel(GetLocalUserUseCase(FakeUserRepository()))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect() }

        assertEquals(SessionState.LoggedOut, viewModel.session.value)
    }

    @Test
    fun `con usuario guardado el estado es Con sesion`() = runTest {
        val ana = User(email = "ana@example.com", name = "Ana", photoUrl = null)
        val users = FakeUserRepository().apply { saveUser(ana) }
        val viewModel = AuthViewModel(GetLocalUserUseCase(users))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.session.collect() }

        assertEquals(SessionState.LoggedIn(ana), viewModel.session.value)
    }
}
