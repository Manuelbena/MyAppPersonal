package com.syncro.presentation.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.syncro.R

/**
 * La mascota de Syncro moviéndose: el vídeo de res/raw en bucle, sin sonido ni controles. Se
 * libera al salir de pantalla. Para fotos fijas (tarjetas pequeñas) ya está la de la frase del día.
 */
@OptIn(UnstableApi::class)
@Composable
fun MascotVideo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/${R.raw.mascot_anim}")))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // Sin el negro de fondo del reproductor: se ve el color de la pantalla hasta el primer fotograma
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        modifier = modifier
    )
}
