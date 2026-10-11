package com.syncro.domain.repository

import kotlinx.coroutines.flow.Flow

/** Las categorías de evento que el usuario ha ocultado en el Calendario (solo en este móvil). */
interface CalendarFilterRepository {
    val hiddenCategories: Flow<Set<String>>
    suspend fun setHiddenCategories(categories: Set<String>)
}
