package com.syncro.presentation.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FloatingBottomNav(
    items: List<AppScreen>,
    currentRoute: String?,
    onItemClick: (AppScreen) -> Unit,
    modifier: Modifier = Modifier,
    /** Número sin leer por ruta (p. ej. mensajes del asistente); 0 o ausente no muestra nada. */
    badges: Map<String, Int> = emptyMap()
) {
    // Se adapta al ancho de la pantalla: en móviles pequeños (≤ 360 dp) los márgenes y las píldoras
    // encogen y cada pestaña se reparte el mismo ancho, así ninguna se queda fuera
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < COMPACT_WIDTH
        val verticalMargin = if (compact) 24.dp else 40.dp
        val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Surface(
            modifier = Modifier
                .padding(
                    start = if (compact) 12.dp else 32.dp,
                    end = if (compact) 12.dp else 32.dp,
                    top = verticalMargin,
                    bottom = bottomBarMargin(verticalMargin, systemBar)
                )
                .fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = if (compact) 4.dp else 8.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { screen ->
                    NavBarItem(
                        screen = screen,
                        isSelected = currentRoute == screen.route,
                        badgeCount = badges[screen.route] ?: 0,
                        pillPadding = if (compact) 14.dp else 24.dp,
                        onClick = { onItemClick(screen) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Por debajo de este ancho de pantalla la barra se compacta. */
private val COMPACT_WIDTH = 380.dp

/** Aire mínimo entre la barra y la botonera del sistema. */
private val SYSTEM_BAR_GAP = 12.dp

/**
 * Margen inferior de la barra: el suyo de siempre, salvo que la botonera del sistema (la de tres
 * botones es más alta que ese margen) la tape; entonces queda justo por encima de ella.
 */
internal fun bottomBarMargin(margin: Dp, systemBar: Dp): Dp = maxOf(margin, systemBar + SYSTEM_BAR_GAP)

@Composable
private fun NavBarItem(
    screen: AppScreen,
    isSelected: Boolean,
    badgeCount: Int,
    pillPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "BgAnimation"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300),
        label = "ContentAnimation"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // El número va en la esquina de la píldora (no dentro, que la recorta)
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) { Text(if (badgeCount > 9) "9+" else badgeCount.toString()) }
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .padding(horizontal = pillPadding, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = if (badgeCount > 0) "${screen.title}, $badgeCount sin leer" else screen.title,
                    tint = contentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = screen.title,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            // "Calendario" no debe partirse ni empujar a las demás en pantallas estrechas
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
