package com.syncro.domain.repository

import com.syncro.domain.model.RepeatSeries

/**
 * Las series de tareas y eventos que se repiten. Solo se guardan en el móvil: a Google llegan sus
 * repeticiones, una a una, como tareas y eventos normales.
 */
interface RepeatSeriesRepository {
    suspend fun getAllSeries(): List<RepeatSeries>
    suspend fun getSeries(id: String): RepeatSeries?
    /** Crea o sustituye la serie con ese id (el id lo asigna el dominio). */
    suspend fun saveSeries(series: RepeatSeries)
    suspend fun deleteSeries(id: String)
}
