package com.syncro.data.repository

import com.syncro.data.local.dao.SavingsAccountDao
import com.syncro.data.local.entity.SavingsAccountEntity
import com.syncro.domain.model.ArgbColor
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.MAIN_ACCOUNT_NAME
import com.syncro.domain.model.SavingsAccount
import com.syncro.domain.model.SavingsAccounts
import com.syncro.domain.repository.SavingsAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

class SavingsAccountRepositoryImpl @Inject constructor(
    private val dao: SavingsAccountDao
) : SavingsAccountRepository {

    override fun observeAccounts(): Flow<SavingsAccounts> = dao.observeAll()
        // Sin cuentas (al cerrar sesión se vacía todo): se crea la principal y la consulta vuelve a emitir
        .onEach { if (it.isEmpty()) dao.upsert(MAIN_ACCOUNT) }
        .filter { it.isNotEmpty() }
        .map { entities ->
            val all = entities.map { it.toDomain() }
            // Si ninguna está marcada (o la marcada ya no existe), se ve la primera
            val active = entities.firstOrNull { it.isActive }?.toDomain() ?: all.first()
            SavingsAccounts(all, active)
        }

    override suspend fun saveAccount(account: SavingsAccount) {
        require(account.id.isNotBlank()) { "La cuenta necesita un id" }
        val existing = dao.getById(account.id)
        dao.upsert(
            SavingsAccountEntity(
                id = account.id,
                name = account.name,
                color = account.color.argb,
                position = existing?.position ?: dao.nextPosition(),
                isActive = existing?.isActive ?: false
            )
        )
    }

    override suspend fun selectAccount(id: String) = dao.setActive(id)

    override suspend fun deleteAccount(id: String) = dao.deleteWithContents(id)

    private fun SavingsAccountEntity.toDomain() = SavingsAccount(id, name, ArgbColor(color))

    private companion object {
        val MAIN_ACCOUNT = SavingsAccountEntity(MAIN_ACCOUNT_ID, MAIN_ACCOUNT_NAME, 0xFF10B981.toInt(), position = 0, isActive = true)
    }
}
