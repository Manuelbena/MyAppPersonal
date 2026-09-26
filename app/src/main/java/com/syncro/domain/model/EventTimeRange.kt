package com.syncro.domain.model

import java.time.LocalTime

/**
 * Un evento ocupa un único día, así que la hora de fin no puede ser anterior a la de inicio.
 * Se admite que sean iguales: los eventos de día completo se guardan como 00:00–00:00.
 */
fun isValidEventTimeRange(start: LocalTime, end: LocalTime): Boolean = !end.isBefore(start)

class InvalidEventTimeRangeException :
    IllegalArgumentException("La hora de fin no puede ser anterior a la de inicio")
