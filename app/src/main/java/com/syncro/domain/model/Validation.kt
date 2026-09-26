package com.syncro.domain.model

import java.time.LocalDateTime

/**
 * Un evento no puede terminar antes de empezar. Se compara fecha y hora, así que un evento de
 * 21:30 a 01:00 del día siguiente es válido. Se admite que coincidan: los eventos de día completo
 * se guardan como 00:00–00:00.
 */
fun isValidEventRange(start: LocalDateTime, end: LocalDateTime): Boolean = !end.isBefore(start)

class InvalidEventTimeRangeException :
    IllegalArgumentException("El evento no puede terminar antes de empezar")

class BlankTitleException :
    IllegalArgumentException("El título no puede estar vacío")
