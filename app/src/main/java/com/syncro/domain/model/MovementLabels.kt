package com.syncro.domain.model

import com.syncro.domain.model.MovementCategory.*

/*
 * Cómo se llama cada categoría y su emoji. Están en el dominio porque los usan también los textos
 * del asistente (avisos de presupuesto); los iconos y colores son de la interfaz y van aparte.
 */

val MovementCategory.label: String
    get() = when (this) {
        HOUSING -> "Vivienda"
        BILLS -> "Facturas"
        GROCERIES -> "Supermercado"
        TRANSPORT -> "Transporte"
        RESTAURANTS -> "Restaurantes"
        LEISURE -> "Ocio"
        SUBSCRIPTIONS -> "Suscripciones"
        HEALTH -> "Salud"
        SHOPPING -> "Compras"
        EDUCATION -> "Educación"
        OTHER_EXPENSE -> "Otros"
        SALARY -> "Nómina"
        EXTRA_WORK -> "Trabajos extra"
        SALES -> "Ventas"
        REFUNDS -> "Devoluciones"
        GIFTS -> "Regalos"
        INVESTMENTS -> "Inversiones"
        OTHER_INCOME -> "Otros"
    }


/** Emoji de la categoría, para los textos que se comparten (WhatsApp no muestra iconos). */
val MovementCategory.emoji: String
    get() = when (this) {
        HOUSING -> "🏠"
        BILLS -> "⚡"
        GROCERIES -> "🛒"
        TRANSPORT -> "🚗"
        RESTAURANTS -> "🍽️"
        LEISURE -> "🎮"
        SUBSCRIPTIONS -> "🔁"
        HEALTH -> "💊"
        SHOPPING -> "🛍️"
        EDUCATION -> "🎓"
        SALARY -> "💼"
        EXTRA_WORK -> "🛠️"
        SALES -> "🏷️"
        REFUNDS -> "↩️"
        GIFTS -> "🎁"
        INVESTMENTS -> "📈"
        OTHER_EXPENSE, OTHER_INCOME -> "📦"
    }

