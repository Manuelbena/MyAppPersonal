package com.syncro.presentation.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Lo que hay que arrastrar en horizontal para cambiar de día. */
private val SWIPE_THRESHOLD = 96.dp

/** Cuánto sigue el contenido al dedo: lo justo para notar que se puede deslizar. */
private const val DRAG_FOLLOW = 0.3f

/**
 * Deslizar a la izquierda pasa al día siguiente y a la derecha al anterior, como pasar páginas.
 * El contenido sigue un poco al dedo y vuelve a su sitio al soltar. Convive con el scroll vertical
 * y con las filas que se desplazan en horizontal: si un hijo consume el gesto, este no hace nada.
 */
fun Modifier.swipeBetweenDays(onPreviousDay: () -> Unit, onNextDay: () -> Unit): Modifier = composed {
    val previous by rememberUpdatedState(onPreviousDay)
    val next by rememberUpdatedState(onNextDay)
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer { translationX = offset.value }
        .pointerInput(Unit) {
            var total = 0f
            fun settle() {
                scope.launch { offset.animateTo(0f) }
            }
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    total += amount
                    scope.launch { offset.snapTo(total * DRAG_FOLLOW) }
                },
                onDragEnd = {
                    val threshold = SWIPE_THRESHOLD.toPx()
                    when {
                        total <= -threshold -> next()
                        total >= threshold -> previous()
                    }
                    settle()
                },
                onDragCancel = { settle() }
            )
        }
}
