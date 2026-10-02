package com.syncro.domain.model

import java.time.LocalTime

/**
 * Los avisos diarios: cada uno se puede apagar y cambiar de hora. La hora de la noche marca además
 * cuándo "acaba el día" para el asistente: desde ella se repasan las pendientes de hoy y ya no se
 * preguntan las prioridades (aunque el aviso de la noche esté apagado).
 */
data class DigestSettings(
    val morningEnabled: Boolean = true,
    val morningTime: LocalTime = DigestMoment.MORNING.defaultTime,
    val eveningEnabled: Boolean = true,
    val eveningTime: LocalTime = DigestMoment.EVENING.defaultTime
) {
    fun isEnabled(moment: DigestMoment): Boolean = when (moment) {
        DigestMoment.MORNING -> morningEnabled
        DigestMoment.EVENING -> eveningEnabled
    }

    fun timeOf(moment: DigestMoment): LocalTime = when (moment) {
        DigestMoment.MORNING -> morningTime
        DigestMoment.EVENING -> eveningTime
    }
}

/** Qué hace el asistente por su cuenta. */
data class AssistantSettings(
    /** Preguntar cada mañana por las prioridades del día. */
    val focusEnabled: Boolean = true,
    /** Preguntar qué hacer con las tareas que se quedaron sin hacer. */
    val leftoversEnabled: Boolean = true,
    /**
     * Día del mes en que se cobra la nómina (1–31; en los meses más cortos, el último día). Null:
     * sin nómina (aún no trabaja, autónomo…), y el asistente no dice nada. Es lo de fábrica.
     */
    val paydayDay: Int? = null
)

/** Ajustes de la app que afectan a la lógica (el tema es solo de la interfaz y va aparte). */
data class AppSettings(
    val digest: DigestSettings = DigestSettings(),
    val assistant: AssistantSettings = AssistantSettings()
)

class InvalidPaydayException :
    IllegalArgumentException("El día de la nómina tiene que estar entre el 1 y el 31")

class InvalidDigestTimesException :
    IllegalArgumentException("El resumen de la mañana tiene que ser antes que el de la noche")
