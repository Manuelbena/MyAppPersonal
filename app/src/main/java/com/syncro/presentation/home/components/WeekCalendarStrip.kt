package com.syncro.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncro.domain.model.DayMark
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Los días de la tira: 8 semanas de lunes a domingo, 4 antes de la de [today] y 3 después. */
fun weekStripDates(today: LocalDate): List<LocalDate> {
    val startMonday = today.minusWeeks(4).minusDays((today.dayOfWeek.value - 1).toLong())
    return (0..55).map { startMonday.plusDays(it.toLong()) }
}

@Composable
fun WeekCalendarStrip(
    today: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    // Un punto bajo los días con tareas o eventos: de color si queda algo, gris si está todo hecho
    dayMarks: Map<LocalDate, DayMark> = emptyMap()
) {
    // Se rehace si cambia el día
    val dates = remember(today) { weekStripDates(today) }

    val screenWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val horizontalPadding = 16.dp 
    val itemWidth = (screenWidth - (horizontalPadding * 2)) / 7

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = 28 
    )

    // Efecto para hacer scroll al día seleccionado si es necesario
    // (Útil para el botón "Hoy")
    LaunchedEffect(selectedDate) {
        val index = dates.indexOfFirst { it.isEqual(selectedDate) }
        if (index != -1) {
            // Intentamos centrar la semana que contiene el día seleccionado
            // El lunes de esa semana sería index - (dia_de_la_semana - 1)
            val dayOfWeekValue = selectedDate.dayOfWeek.value
            val mondayIndex = index - (dayOfWeekValue - 1)
            listState.animateScrollToItem(if (mondayIndex >= 0) mondayIndex else index)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = horizontalPadding)
    ) {
        items(dates) { date ->
            val isSelected = date.isEqual(selectedDate)
            val isToday = date.isEqual(today)
            
            DayItem(
                date = date,
                isSelected = isSelected,
                isToday = isToday,
                mark = dayMarks[date],
                width = itemWidth,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
private fun DayItem(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    mark: DayMark?,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() }
        .take(1) 
    val dayNumber = date.dayOfMonth.toString()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = dayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = dayNumber,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                },
                fontSize = 15.sp
            )
        }
        
        // Hoy ya se distingue por su fondo: el punto dice si el día tiene algo
        Box(modifier = Modifier.height(8.dp)) {
            if (mark != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            if (mark == DayMark.PENDING) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}
