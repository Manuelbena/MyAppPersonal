package com.syncro.domain.usecase

import com.syncro.domain.repository.MovementRepository
import javax.inject.Inject

/** Borra un movimiento; si es mensual, desaparece de todos los meses. */
class DeleteMovementUseCase @Inject constructor(
    private val repository: MovementRepository
) {
    suspend operator fun invoke(id: String) = repository.deleteMovement(id)
}
