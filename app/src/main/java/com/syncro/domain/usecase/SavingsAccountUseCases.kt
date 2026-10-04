package com.syncro.domain.usecase

import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.InvalidAccountException
import com.syncro.domain.model.MAX_ACCOUNT_NAME_LENGTH
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import com.syncro.domain.model.toSentenceCase
import com.syncro.domain.repository.SavingsAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/** Las cuentas de ahorro y la que se está viendo. */
class ObserveSavingsAccountsUseCase @Inject constructor(
    private val repository: SavingsAccountRepository
) {
    operator fun invoke(): Flow<SavingsAccounts> = repository.observeAccounts()
}

/** Cambia la cuenta que se ve en Ahorros e Inicio. */
class SelectSavingsAccountUseCase @Inject constructor(
    private val repository: SavingsAccountRepository
) {
    suspend operator fun invoke(id: String) = repository.selectAccount(id)
}

class SaveSavingsAccountUseCase @Inject constructor(
    private val repository: SavingsAccountRepository
) {
    /**
     * Crea una cuenta (sin [id]; pasa a ser la que se ve) o cambia el nombre y el color de una.
     * Falla sin guardar con [InvalidAccountException] si el nombre está vacío, es demasiado largo
     * o ya lo tiene otra cuenta (sin distinguir mayúsculas): dos "Conjunta" serían un lío.
     */
    suspend operator fun invoke(name: String, color: ArgbColor, id: String? = null): Result<SavingsAccount> {
        val clean = name.trim().toSentenceCase()
        if (clean.isBlank()) return Result.failure(InvalidAccountException("Ponle un nombre a la cuenta"))
        if (clean.length > MAX_ACCOUNT_NAME_LENGTH) {
            return Result.failure(InvalidAccountException("Un nombre más corto, de $MAX_ACCOUNT_NAME_LENGTH letras como mucho"))
        }
        val others = repository.observeAccounts().first().all.filter { it.id != id }
        if (others.any { it.name.equals(clean, ignoreCase = true) }) {
            return Result.failure(InvalidAccountException("Ya tienes una cuenta que se llama así"))
        }
        val account = SavingsAccount(id ?: UUID.randomUUID().toString(), clean, color)
        repository.saveAccount(account)
        if (id == null) repository.selectAccount(account.id)
        return Result.success(account)
    }
}

class DeleteSavingsAccountUseCase @Inject constructor(
    private val repository: SavingsAccountRepository
) {
    /**
     * Borra una cuenta con sus ingresos, gastos y presupuestos. Falla con [InvalidAccountException]
     * si es la única (siempre tiene que haber una). Si era la que se veía, se pasa a la primera.
     */
    suspend operator fun invoke(id: String): Result<Unit> {
        val accounts = repository.observeAccounts().first()
        if (accounts.all.none { it.id == id }) return Result.success(Unit)
        if (!accounts.hasSeveral) return Result.failure(InvalidAccountException("Necesitas al menos una cuenta"))
        repository.deleteAccount(id)
        if (accounts.active.id == id) repository.selectAccount(accounts.all.first { it.id != id }.id)
        return Result.success(Unit)
    }
}
