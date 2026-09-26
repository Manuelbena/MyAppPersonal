package com.syncro.presentation.components

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val DISPLAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Hora tal como se muestra en pantalla ("09:05"). */
fun LocalTime.toDisplayTime(): String = format(DISPLAY_TIME_FORMAT)
