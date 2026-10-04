package com.syncro.domain.usecase

import com.syncro.domain.model.BackupContent
import com.syncro.domain.model.InvalidBackupException
import com.syncro.domain.model.MAIN_ACCOUNT_ID
import com.syncro.domain.model.RestoreSummary
import com.syncro.domain.model.movementsCsv
import com.syncro.domain.repository.BackupCodec
import com.syncro.domain.repository.BudgetRepository
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.NoteRepository
import com.syncro.domain.repository.SavingsAccountRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Todos los ingresos y gastos en CSV (para Excel o Google Sheets), con su cuenta si hay varias. */
class ExportMovementsCsvUseCase @Inject constructor(
    private val movements: MovementRepository,
    private val accounts: SavingsAccountRepository
) {
    suspend operator fun invoke(): String {
        val names = accounts.observeAccounts().first().all.associate { it.id to it.name }
        return movementsCsv(movements.getAllMovements(), names)
    }
}

/** El texto de una copia con lo que solo existe en el móvil: notas, cuentas, movimientos y presupuestos. */
class CreateBackupUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val movements: MovementRepository,
    private val budgets: BudgetRepository,
    private val accounts: SavingsAccountRepository,
    private val codec: BackupCodec
) {
    suspend operator fun invoke(): String = codec.encode(
        BackupContent(
            notes = notes.getAllNotes().first(),
            movements = movements.getAllMovements(),
            budgets = budgets.observeBudgets().first(),
            accounts = accounts.observeAccounts().first().all
        )
    )
}

class RestoreBackupUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val movements: MovementRepository,
    private val budgets: BudgetRepository,
    private val accounts: SavingsAccountRepository,
    private val codec: BackupCodec
) {
    /**
     * Recupera una copia sin borrar nada: lo que no está se añade y lo que ya está (mismo id, o
     * misma cuenta y categoría en los presupuestos) se actualiza con lo de la copia. Las cuentas se
     * recuperan antes; lo que sea de una cuenta que no existe va a la principal. Falla sin tocar
     * nada con [InvalidBackupException] si el archivo no es una copia de Syncro.
     */
    suspend operator fun invoke(text: String): Result<RestoreSummary> {
        val content = try {
            codec.decode(text)
        } catch (e: InvalidBackupException) {
            return Result.failure(e)
        }
        content.accounts.forEach { accounts.saveAccount(it) }
        val known = accounts.observeAccounts().first().all.map { it.id }.toSet()
        fun accountOf(id: String) = id.takeIf { it in known } ?: MAIN_ACCOUNT_ID

        content.notes.forEach { notes.insertNote(it) }
        content.movements.forEach { movements.insertMovement(it.copy(accountId = accountOf(it.accountId))) }
        content.budgets.forEach { budgets.saveBudget(it.copy(accountId = accountOf(it.accountId))) }
        return Result.success(RestoreSummary(content.notes.size, content.movements.size, content.budgets.size))
    }
}
