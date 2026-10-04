package com.syncro.domain.repository

import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import kotlinx.coroutines.flow.Flow

/** Las cuentas de ahorro (solo en el móvil, como los movimientos). Siempre hay al menos una. */
interface SavingsAccountRepository {
    /** Las cuentas y la elegida; si no hay ninguna (p. ej. tras cerrar sesión) crea la principal. */
    fun observeAccounts(): Flow<SavingsAccounts>
    /** Crea o renombra/recolorea (por id); una nueva va al final. */
    suspend fun saveAccount(account: SavingsAccount)
    suspend fun selectAccount(id: String)
    /** Borra la cuenta con sus movimientos y presupuestos. */
    suspend fun deleteAccount(id: String)
}
