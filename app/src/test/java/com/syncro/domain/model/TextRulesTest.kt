package com.syncro.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** [toSentenceCase]: primera letra en mayúscula, el resto intacto. */
class TextRulesTest {

    @Test
    fun `pone en mayuscula la primera letra`() = assertEquals("Tomar creatina", "tomar creatina".toSentenceCase())

    @Test
    fun `funciona con letras del español`() {
        assertEquals("Ñandú", "ñandú".toSentenceCase())
        assertEquals("Éxito", "éxito".toSentenceCase())
    }

    @Test
    fun `respeta siglas y mayusculas del resto del texto`() = assertEquals("Ir a IKEA", "ir a IKEA".toSentenceCase())

    @Test
    fun `si ya empieza en mayuscula no cambia`() = assertEquals("Reunión", "Reunión".toSentenceCase())

    @Test
    fun `quita los espacios de alrededor antes de mirar la primera letra`() =
        assertEquals("Cena", "   cena  ".toSentenceCase())

    @Test
    fun `si empieza por un numero lo deja igual`() = assertEquals("3 cafés", "3 cafés".toSentenceCase())

    @Test
    fun `un texto vacio sigue vacio`() = assertEquals("", "   ".toSentenceCase())
}
