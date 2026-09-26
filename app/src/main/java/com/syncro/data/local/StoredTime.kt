package com.syncro.data.local

import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Room guarda las horas como texto "HH:mm" (el código de sincronización con Google también lo
// usa así). El dominio trabaja con LocalTime; la conversión se hace solo al entrar/salir de Room.

private val STORED_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalTime.toStoredTime(): String = format(STORED_TIME_FORMAT)

/** Un valor corrupto en la base de datos no debe tumbar la app: se muestra a las 00:00. */
fun String.toLocalTimeOrMidnight(): LocalTime =
    runCatching { LocalTime.parse(this, STORED_TIME_FORMAT) }
        .onFailure { Log.w("StoredTime", "Invalid stored time '$this', using 00:00") }
        .getOrDefault(LocalTime.MIDNIGHT)
