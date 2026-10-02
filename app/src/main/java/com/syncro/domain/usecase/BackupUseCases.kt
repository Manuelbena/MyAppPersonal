package com.syncro.domain.usecase

import com.syncro.domain.model.BackupContent
import com.syncro.domain.model.InvalidBackupException
import com.syncro.domain.model.RestoreSummary
import com.syncro.domain.model.movementsCsv
import com.syncro.domain.repository.BackupCodec
import com.syncro.domain.repository.BudgetRepository
import com.syncro.domain.repository.MovementRepository
import com.syncro.domain.repository.NoteRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Todos los ingresos y gastos en CSV (para Excel o Google Sheets). */
class ExportMovementsCsvUseCase @Inject constructor(
    private val movements: MovementRepository
) {
    suspend operator fun invoke(): String = movementsCsv(movements.getAllMovements())
}

/** El texto de una copia con lo que solo existe en el móvil: notas, movimientos y presupuestos. */
class CreateBackupUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val movements: MovementRepository,
    private val budgets: BudgetRepository,
    private val codec: BackupCodec
) {
    suspend operator fun invoke(): String = codec.encode(
        BackupContent(
            notes = notes.getAllNotes().first(),
            movements = movements.getAllMovements(),
            budgets = budgets.observeBudgets().first()
        )
    )
}

class RestoreBackupUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val movements: MovementRepository,
    private val budgets: BudgetRepository,
    private val codec: BackupCodec
) {
    /**
     * Recupera una copia sin borrar nada: lo que no está se añade y lo que ya está (mismo id, o
     * misma categoría en los presupuestos) se actualiza con lo de la copia. Falla sin tocar nada
     * con [InvalidBackupException] si el archivo no es una copia de Syncro.
     */
    suspend operator fun invoke(text: String): Result<RestoreSummary> {
        val content = try {
            codec.decode(text)
        } catch (e: InvalidBackupException) {
            return Result.failure(e)
        }
        content.notes.forEach { notes.insertNote(it) }
        content.movements.forEach { movements.insertMovement(it) }
        content.budgets.forEach { budgets.saveBudget(it) }
        return Result.success(RestoreSummary(content.notes.size, content.movements.size, content.budgets.size))
    }
}
