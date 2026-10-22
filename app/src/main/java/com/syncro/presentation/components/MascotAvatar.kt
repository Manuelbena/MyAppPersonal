package com.syncro.presentation.components

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.syncro.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La cara de la mascota (la cafetera) en quieto: un fotograma del vídeo de res/raw. Es la foto del
 * asistente en el chat y la de la frase del día. Mientras se saca el fotograma (la primera vez) brilla
 * como un esqueleto de carga; si no se puede, se ve la chispa del asistente sobre el mismo fondo.
 */
@Composable
fun MascotAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    // Mientras se saca el fotograma la foto brilla como el resto de esqueletos; la chispa queda solo si falla
    var failed by remember { mutableStateOf(false) }
    val frame by produceState(MascotFrame.cached, context) {
        if (value == null) value = MascotFrame.load(context).also { failed = it == null }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val image = frame
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!failed) {
            ShimmerHost { SkeletonBlock(Modifier.fillMaxSize(), shape) }
        } else {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

/**
 * El fotograma se saca una sola vez por proceso con MediaMetadataRetriever (lo del sistema, sin
 * decodificadores de terceros) y se guarda: el chat pinta muchas fotos a la vez.
 * Regresión: con el decodificador de vídeo de Coil la foto se quedaba en la chispa.
 */
private object MascotFrame {
    /** Un poco después del inicio, cuando la cafetera ya está entera en pantalla. */
    private const val FRAME_TIME_US = 500_000L
    /** De sobra para la foto más grande (96 dp a densidad alta) sin guardar el vídeo a tamaño completo. */
    private const val MAX_SIDE_PX = 384

    @Volatile
    var cached: ImageBitmap? = null
        private set

    suspend fun load(context: Context): ImageBitmap? = cached ?: withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse("android.resource://${context.packageName}/${R.raw.mascot_anim}"))
            val frame = retriever.getFrameAtTime(FRAME_TIME_US, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return@withContext null
            val scale = MAX_SIDE_PX.toFloat() / maxOf(frame.width, frame.height)
            val bitmap = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(frame, (frame.width * scale).toInt(), (frame.height * scale).toInt(), true)
                    .also { if (it !== frame) frame.recycle() }
            } else {
                frame
            }
            bitmap.asImageBitmap().also { cached = it }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
