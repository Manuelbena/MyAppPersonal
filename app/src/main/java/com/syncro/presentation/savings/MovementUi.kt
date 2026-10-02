package com.syncro.presentation.savings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.syncro.domain.model.MovementCategory
import com.syncro.domain.model.MovementCategory.*
import com.syncro.domain.model.MovementType
import com.syncro.presentation.theme.*
import java.text.NumberFormat
import java.util.Locale

/*
 * Cómo se ven los movimientos: nombre, icono y color de cada categoría, y los importes en euros.
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

val MovementCategory.icon: ImageVector
    get() = when (this) {
        HOUSING -> Icons.Rounded.Home
        BILLS -> Icons.Rounded.Bolt
        GROCERIES -> Icons.Rounded.ShoppingCart
        TRANSPORT -> Icons.Rounded.DirectionsCar
        RESTAURANTS -> Icons.Rounded.Restaurant
        LEISURE -> Icons.Rounded.SportsEsports
        SUBSCRIPTIONS -> Icons.Rounded.Autorenew
        HEALTH -> Icons.Rounded.Favorite
        SHOPPING -> Icons.Rounded.ShoppingBag
        EDUCATION -> Icons.Rounded.School
        OTHER_EXPENSE -> Icons.Rounded.MoreHoriz
        SALARY -> Icons.Rounded.Work
        EXTRA_WORK -> Icons.Rounded.Handyman
        SALES -> Icons.Rounded.Sell
        REFUNDS -> Icons.AutoMirrored.Rounded.Undo
        GIFTS -> Icons.Rounded.CardGiftcard
        INVESTMENTS -> Icons.AutoMirrored.Rounded.ShowChart
        OTHER_INCOME -> Icons.Rounded.MoreHoriz
    }

val MovementCategory.color: Color
    get() = when (this) {
        HOUSING -> Indigo500
        BILLS -> Amber500
        GROCERIES -> Emerald500
        TRANSPORT -> Sky500
        RESTAURANTS -> Pink500
        LEISURE -> Violet500
        SUBSCRIPTIONS -> Rose500
        HEALTH -> Color(0xFFFF5252)
        SHOPPING -> Amber600
        EDUCATION -> Blue500
        SALARY -> Emerald500
        EXTRA_WORK -> Sky500
        SALES -> Indigo500
        REFUNDS -> Amber500
        GIFTS -> Pink500
        INVESTMENTS -> Violet500
        OTHER_EXPENSE, OTHER_INCOME -> Slate500
    }

/** Verde lo que entra, rojo lo que sale. */
val MovementType.color: Color
    get() = if (this == MovementType.INCOME) Emerald500 else Rose500

private val SPANISH = Locale("es", "ES")

/** "1.234,56 €" (siempre dos decimales, como un extracto bancario). */
fun formatEuros(cents: Long): String =
    NumberFormat.getCurrencyInstance(SPANISH).apply {
        currency = java.util.Currency.getInstance("EUR")
    }.format(java.math.BigDecimal.valueOf(cents, 2))

/** Con signo: "+1.200,00 €" para ingresos y "−45,90 €" para gastos. */
fun formatSignedEuros(cents: Long, type: MovementType): String =
    (if (type == MovementType.INCOME) "+" else "−") + formatEuros(cents)
