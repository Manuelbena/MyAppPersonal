package com.syncro.domain.usecase

import com.syncro.domain.repository.CalendarFilterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Las categorías de evento ocultas en el Calendario. */
class ObserveHiddenCategoriesUseCase @Inject constructor(
    private val repository: CalendarFilterRepository
) {
    operator fun invoke(): Flow<Set<String>> = repository.hiddenCategories
}

/** Muestra u oculta una categoría en el Calendario (p. ej. Trabajo el fin de semana). */
class SetCategoryHiddenUseCase @Inject constructor(
    private val repository: CalendarFilterRepository
) {
    suspend operator fun invoke(category: String, hidden: Boolean) {
        val current = repository.hiddenCategories.first()
        repository.setHiddenCategories(if (hidden) current + category else current - category)
    }

    /** Vuelve a mostrar todas. */
    suspend fun showAll() = repository.setHiddenCategories(emptySet())
}
