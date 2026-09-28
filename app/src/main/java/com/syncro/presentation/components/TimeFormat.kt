package com.syncro.presentation.components

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale("es", "ES")
private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH)
private val SHORT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", SPANISH)

/** Hora tal como se muestra en pantalla ("09:05"). */
fun LocalTime.toDisplayTime(): String = format(DISPLAY_TIME_FORMAT)

/** "Sábado, 26 de septiembre". */
fun LocalDate.toLongDisplayDate(): String = format(LONG_DATE_FORMAT).replaceFirstChar { it.titlecase(SPANISH) }

/** "sáb 26 sept". */
fun LocalDate.toShortDisplayDate(): String = format(SHORT_DATE_FORMAT)
