package com.syncro.data.repository

import com.syncro.data.local.dao.MovementDao
import com.syncro.data.local.entity.MovementEntity
import com.syncro.domain.model.Movement
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementType
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.repository.MovementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class MovementRepositoryImpl @Inject constructor(
    private val dao: MovementDao
) : MovementRepository {

    override fun observeForPeriod(period: SavingsPeriod): Flow<List<Movement>> =
        dao.observeForRange(period.start.toEpochDay(), period.end.toEpochDay())
            .map { entities -> entities.mapNotNull { it.toDomain() } }

    override suspend fun getAllMovements(): List<Movement> = dao.getAll().mapNotNull { it.toDomain() }

    override suspend fun insertMovement(movement: Movement) {
        require(movement.id.isNotBlank()) { "El movimiento necesita un id" }
        dao.insertMovement(
            MovementEntity(
                id = movement.id,
                type = movement.type.name,
                amountCents = movement.amountCents,
                category = movement.category.name,
                date = movement.date.toEpochDay(),
                note = movement.note,
                repeatsMonthly = movement.repeatsMonthly
            )
        )
    }

    override suspend fun deleteMovement(id: String) = dao.deleteMovement(id)

    /**
     * Null si el tipo no se reconoce (fila corrupta): mejor no mostrarla que sumarla mal. Una
     * categoría que ya no existe (renombrada en otra versión) pasa a "Otros" de su tipo.
     */
    private fun MovementEntity.toDomain(): Movement? {
        val type = MovementType.entries.firstOrNull { it.name == type } ?: return null
        val category = MovementCategory.entries.firstOrNull { it.name == category && it.type == type }
            ?: MovementCategory.other(type)
        return Movement(
            id = id,
            type = type,
            amountCents = amountCents,
            category = category,
            date = LocalDate.ofEpochDay(date),
            note = note,
            repeatsMonthly = repeatsMonthly
        )
    }
}
