package com.syncro.domain.model

import java.text.Normalizer

/*
 * Categoría de un evento adivinada por su título ("Dentista" → Salud), para que casi nunca haya
 * que elegirla a mano. La usan el formulario (mientras se escribe) y la sincronización (eventos que
 * llegan de Google sin color). Se compara sin tildes ni mayúsculas y por palabras enteras ("super"
 * no salta con "superar"); una palabra acabada en "*" es una raíz ("psicolog*": psicólogo, psicóloga).
 */

private val CATEGORY_KEYWORDS: List<Pair<String, List<String>>> = listOf(
    "Trabajo" to listOf(
        "trabajo", "reunion", "meeting", "oficina", "cliente*", "entrevista", "presentacion", "daily",
        "sprint", "llamada con", "videollamada", "deadline", "entrega"
    ),
    "Salud" to listOf(
        "salud", "medico", "medica", "doctor*", "dentista", "hospital", "consulta", "analitica*", "fisio*",
        "psicolog*", "vacuna", "farmacia", "revision", "oculista", "ginecolog*", "pediatra", "urgencias"
    ),
    "Deporte" to listOf(
        "deporte", "gimnasio", "gym", "correr", "running", "yoga", "pilates", "futbol", "padel", "tenis",
        "natacion", "piscina", "entreno", "entrenamiento", "crossfit", "bici", "spinning", "partido"
    ),
    "Ocio" to listOf(
        "ocio", "cine", "concierto", "cena", "comida con", "restaurante", "cañas", "fiesta", "teatro",
        "museo", "viaje", "vacaciones", "festival", "brunch", "copas", "escapada"
    ),
    "Compras" to listOf(
        "compras", "compra", "comprar", "super", "supermercado", "mercadona", "lidl", "carrefour",
        "tienda", "rebajas", "ikea", "regalo*"
    ),
    "Recados" to listOf(
        "recados", "recado", "recoger", "banco", "correos", "itv", "taller", "gestoria", "notaria",
        "ayuntamiento", "renovar", "dni", "pasaporte", "tintoreria", "devolver"
    ),
    "Personal" to listOf(
        "personal", "cumpleaños", "cumple", "aniversario", "familia", "mama", "papa", "abuela", "abuelo",
        "peluqueria"
    )
).map { (category, words) -> category to words.map(::normalizeForMatch) }

/**
 * La categoría que sugiere [title], o null si ninguna palabra encaja. Si encajan varias gana la que
 * aparece antes en el título: "Comprar regalo de cumpleaños" es Compras, no Personal.
 */
fun suggestEventCategory(title: String): String? {
    val text = normalizeForMatch(title)
    if (text.isBlank()) return null
    return CATEGORY_KEYWORDS
        .mapNotNull { (category, words) -> words.mapNotNull { text.wordIndex(it) }.minOrNull()?.let { category to it } }
        .minByOrNull { it.second }
        ?.first
}

/** Si [text] contiene alguna de [words] (mismas reglas que [suggestEventCategory]). */
internal fun containsAnyWord(text: String, words: List<String>): Boolean {
    val normalized = normalizeForMatch(text)
    return words.any { normalized.wordIndex(normalizeForMatch(it)) != null }
}

/** Dónde aparece [word] como palabra entera (o como comienzo de palabra si acaba en "*"), o null. */
private fun String.wordIndex(word: String): Int? {
    val isStem = word.endsWith("*")
    val needle = word.removeSuffix("*")
    var from = 0
    while (true) {
        val index = indexOf(needle, from)
        if (index < 0) return null
        val end = index + needle.length
        val startsWord = index == 0 || !this[index - 1].isLetterOrDigit()
        val endsWord = isStem || end == length || !this[end].isLetterOrDigit()
        if (startsWord && endsWord) return index
        from = index + 1
    }
}

/** Minúsculas y sin tildes; la ñ se queda ("cañas" no es "canas"). */
private fun normalizeForMatch(text: String): String =
    Normalizer.normalize(text.lowercase().replace('ñ', '\u0001'), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace('\u0001', 'ñ')
