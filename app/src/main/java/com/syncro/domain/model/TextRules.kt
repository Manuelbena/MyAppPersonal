package com.syncro.domain.model

import java.util.Locale

private val SPANISH = Locale("es", "ES")

/**
 * Texto introducido por el usuario tal como se guarda: sin espacios alrededor y con la primera
 * letra en mayúscula ("ñandú" -> "Ñandú"). El resto no se toca, para respetar siglas y nombres
 * ("ir a IKEA" -> "Ir a IKEA").
 */
fun String.toSentenceCase(): String =
    trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(SPANISH) else it.toString() }
