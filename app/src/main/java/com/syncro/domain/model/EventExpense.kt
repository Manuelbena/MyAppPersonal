package com.syncro.domain.model

/*
 * Puente entre la agenda y Ahorros: al completar un evento en el que lo normal es gastar dinero
 * (una compra, una cena, el dentista) se ofrece apuntar el gasto con la categoría ya elegida.
 */

private val GROCERY_WORDS = listOf("super", "supermercado", "mercadona", "lidl", "carrefour", "compra semanal", "compra del mes")
private val RESTAURANT_WORDS = listOf("cena", "comida", "restaurante", "cañas", "brunch", "desayuno", "copas", "bar")

/** La categoría de gasto que encaja con este evento, o null si no suele costar dinero. */
fun SyncroItem.Event.suggestedExpenseCategory(): MovementCategory? = when (categoryText) {
    "Compras" -> if (containsAnyWord(title, GROCERY_WORDS)) MovementCategory.GROCERIES else MovementCategory.SHOPPING
    "Ocio" -> if (containsAnyWord(title, RESTAURANT_WORDS)) MovementCategory.RESTAURANTS else MovementCategory.LEISURE
    "Salud" -> MovementCategory.HEALTH
    else -> null
}

/** Un gasto a medio apuntar a partir de un evento: la categoría y la nota (el título del evento). */
data class ExpenseDraft(val category: MovementCategory, val note: String)

/** El gasto que se ofrece apuntar al completar este evento, o null si no toca. */
fun SyncroItem.Event.expenseDraft(): ExpenseDraft? = suggestedExpenseCategory()?.let { ExpenseDraft(it, title) }
