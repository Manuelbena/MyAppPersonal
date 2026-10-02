package com.syncro.domain.usecase

import com.syncro.domain.model.InvalidAmountException
import com.syncro.domain.model.MAX_AMOUNT_CENTS
import com.syncro.domain.model.Movement
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.toSentenceCase
import com.syncro.domain.repository.MovementRepository
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class SaveMovementUseCase @Inject constructor(
    private val repository: MovementRepository
) {
    /**
     * Registra un ingreso o un gasto. Falla sin guardar nada con [InvalidAmountException] si el
     * importe no es mayor que 0 (o es desorbitado). Si la categoría es del otro tipo (un gasto en
     * "Nómina"), se guarda en "Otros" del tipo correcto en vez de mezclar los totales.
     */
    suspend operator fun invoke(
        type: MovementType,
        amountCents: Long,
        category: MovementCategory,
        date: LocalDate,
        note: String,
        repeatsMonthly: Boolean
    ): Result<Unit> {
        if (amountCents !in 1..MAX_AMOUNT_CENTS) return Result.failure(InvalidAmountException())

        val movement = Movement(
            id = UUID.randomUUID().toString(),
            type = type,
            amountCents = amountCents,
            category = category.takeIf { it.type == type } ?: MovementCategory.other(type),
            date = date,
            note = note.toSentenceCase().ifBlank { null },
            repeatsMonthly = repeatsMonthly
        )
        repository.insertMovement(movement)
        return Result.success(Unit)
    }
}
