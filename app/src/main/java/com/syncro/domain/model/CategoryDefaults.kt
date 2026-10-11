package com.syncro.domain.model

/*
 * Lo que cada categoría de evento suele llevar, para rellenar el formulario sin pensar: un aviso
 * razonable (al médico se va mejor avisado el día antes) y subtareas típicas que se añaden con un
 * toque. Son sugerencias: lo que el usuario elige a mano siempre gana.
 */

/**
 * El aviso por defecto de un evento nuevo de [category], en minutos antes de empezar (ver
 * `reminderAt`), o null si esa categoría no lo necesita. Los de todo el día usan horas del día.
 */
fun defaultReminderFor(category: String, isAllDay: Boolean): Int? = when (category) {
    // Al médico: el día antes, para preparar la tarjeta o pedir el día libre
    "Salud" -> if (isAllDay) DAY_BEFORE_AT_20 else 24 * 60
    "Trabajo" -> if (isAllDay) SAME_DAY_AT_9 else 15
    "Deporte" -> if (isAllDay) SAME_DAY_AT_9 else 30
    "Recados" -> if (isAllDay) SAME_DAY_AT_9 else 60
    else -> null
}

/** Subtareas que suelen hacer falta en un evento de [category] (vacío si no hay ninguna típica). */
fun suggestedSubtasksFor(category: String): List<String> = when (category) {
    "Salud" -> listOf("Llevar la tarjeta sanitaria", "Apuntar las dudas", "Llevar informes o recetas")
    "Deporte" -> listOf("Preparar la bolsa", "Llevar agua", "Toalla y candado")
    "Compras" -> listOf("Hacer la lista", "Llevar bolsas", "Mirar ofertas")
    "Recados" -> listOf("Llevar el DNI", "Revisar los papeles", "Comprobar el horario")
    "Trabajo" -> listOf("Preparar el orden del día", "Repasar las notas", "Enviar el resumen")
    "Ocio" -> listOf("Reservar", "Comprar entradas", "Avisar al grupo")
    else -> emptyList()
}

/** "Ese día a las 9:00" en un evento de todo el día (empieza a las 00:00: 9 h después). */
private const val SAME_DAY_AT_9 = -9 * 60
/** "El día antes a las 20:00": 4 h antes de las 00:00. */
private const val DAY_BEFORE_AT_20 = 4 * 60
