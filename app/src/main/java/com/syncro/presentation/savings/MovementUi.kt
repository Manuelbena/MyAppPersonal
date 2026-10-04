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
import com.syncro.domain.model.SavingsPeriod
import com.syncro.domain.model.euros
import com.syncro.presentation.theme.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * Cómo se ven los movimientos: icono y color de cada categoría (el nombre y el emoji están en el
 * dominio, MovementLabels.kt) y los importes en euros.
 */

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

/** "1.234,56 €" (siempre dos decimales, como un extracto bancario). */
fun formatEuros(cents: Long): String = euros(cents)

/** Con signo: "+1.200,00 €" para ingresos y "−45,90 €" para gastos. */
fun formatSignedEuros(cents: Long, type: MovementType): String =
    (if (type == MovementType.INCOME) "+" else "−") + formatEuros(cents)

private val SPANISH = Locale("es", "ES")

/** "27 sep": el mes en tres letras a mano (lo abreviado de Java cambia según versión: "sept.", "sep"). */
private fun LocalDate.shortDay(): String =
    "$dayOfMonth " + month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH).take(3)

/**
 * El mes que se ve en Ahorros: "Octubre 2026" si es el mes natural; de nómina a nómina, las
 * fechas: "27 sep – 26 oct 2026".
 */
fun SavingsPeriod.title(): String =
    if (isCalendarMonth) {
        month.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH).replaceFirstChar { it.uppercase() } + " ${month.year}"
    } else {
        "${start.shortDay()} – ${end.shortDay()} ${end.year}"
    }

/** Para una frase: "octubre de 2026" o "27 sep – 26 oct 2026" (resumen del mes). */
fun SavingsPeriod.name(): String =
    if (isCalendarMonth) month.month.getDisplayName(TextStyle.FULL_STANDALONE, SPANISH) + " de ${month.year}" else title()

/** El importe como se teclea en el formulario, para editarlo: "45,90" o "1200" (sin miles ni €). */
internal fun Long.toAmountInput(): String =
    if (this % 100 == 0L) (this / 100).toString() else "%d,%02d".format(this / 100, this % 100)
