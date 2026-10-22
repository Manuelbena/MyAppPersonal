package com.syncro.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Carga con "esqueleto", como Facebook: mientras una pantalla lee sus datos se ven bloques grises
 * con la forma de lo que va a salir y un reflejo que los recorre. Así no hay saltos (cosas que se
 * pintan y luego cambian): la pantalla real sale de una vez, con un fundido, cuando está todo.
 */

/** Posición del reflejo, compartida por todos los bloques de la pantalla para que vaya a la vez. */
private val LocalShimmerOffset = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * Hace de raíz de un esqueleto: anima un único reflejo que cruza la pantalla en diagonal, y todos
 * los [SkeletonBlock] de dentro lo dibujan en su sitio (parece una sola luz que pasa por encima).
 */
@Composable
fun ShimmerHost(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // Recorrido de la luz: de fuera por la izquierda hasta pasar el último bloque de abajo a la derecha
    val (from, distance) = with(density) {
        val band = SHIMMER_BAND.toPx()
        -band to configuration.screenWidthDp.dp.toPx() + configuration.screenHeightDp.dp.toPx() * SHIMMER_SLOPE + band * 2
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress"
    )
    CompositionLocalProvider(LocalShimmerOffset provides { from + progress * distance }) {
        Box(Modifier.semantics { contentDescription = "Cargando" }) { content() }
    }
}

/** Ancho de la franja de luz y su inclinación (cuánto avanza en horizontal por cada píxel que baja). */
private val SHIMMER_BAND = 90.dp
private const val SHIMMER_SLOPE = 0.3f

/** Un bloque gris del esqueleto con el reflejo pasando. Sin tamaño: se lo da [modifier]. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(10.dp)) {
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
    val offset = LocalShimmerOffset.current
    // Dónde está el bloque en pantalla: el reflejo va en coordenadas de pantalla, así cruza todos
    // los bloques alineado, como una sola luz
    val origin = remember { FloatArray(2) }
    Box(
        modifier
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                origin[0] = position.x
                origin[1] = position.y
            }
            .clip(shape)
            .background(base)
            .drawWithContent {
                val x = offset() - origin[0] - origin[1] * SHIMMER_SLOPE
                val band = SHIMMER_BAND.toPx()
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, band * SHIMMER_SLOPE * 2)
                    )
                )
            }
    )
}

/** Una línea de texto del esqueleto. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.width(width).height(height), RoundedCornerShape(50))
}

/** Un círculo del esqueleto (fotos, iconos, checks). */
@Composable
fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    SkeletonBlock(modifier.size(size), CircleShape)
}

/**
 * Enseña [skeleton] hasta que [isReady] y entonces [content], con un fundido. Mientras no esté lista
 * la pantalla real ni se compone, así no se ven a medias las piezas que llegan antes.
 */
@Composable
fun LoadingContent(
    isReady: Boolean,
    skeleton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedContent(
        targetState = isReady,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        modifier = modifier,
        label = "loadingContent"
    ) { ready ->
        if (ready) content() else ShimmerHost(skeleton)
    }
}
