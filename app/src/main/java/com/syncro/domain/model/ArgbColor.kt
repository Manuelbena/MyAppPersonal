package com.syncro.domain.model

/**
 * Color elegido por el usuario o asignado por Google (categorías, notas), en formato ARGB.
 *
 * Es un dato del dominio, así que no usa el `Color` de Compose: el dominio no debe depender de
 * la UI. La conversión a `Color` vive en `presentation/theme/ColorMapping.kt`.
 * Al ser `value class` no ocupa más que un Int en tiempo de ejecución.
 */
@JvmInline
value class ArgbColor(val argb: Int) {
    /** Permite escribir literales como `ArgbColor(0xFF6366F1)`. */
    constructor(argb: Long) : this(argb.toInt())
}
