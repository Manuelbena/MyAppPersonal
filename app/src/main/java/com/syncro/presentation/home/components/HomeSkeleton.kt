package com.syncro.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.syncro.presentation.components.SkeletonBlock
import com.syncro.presentation.components.SkeletonCircle
import com.syncro.presentation.components.SkeletonLine

/**
 * Inicio mientras carga: la cabecera, la tira de la semana, una tarjeta, la de tareas y unos
 * eventos del timeline, en gris y con el reflejo. Mismas medidas que lo real, para que al llegar
 * los datos nada se mueva de sitio.
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        // Cabecera: saludo, fecha y la foto
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SkeletonLine(width = 170.dp, height = 28.dp)
                Spacer(Modifier.height(10.dp))
                SkeletonLine(width = 120.dp, height = 14.dp)
            }
            SkeletonCircle(48.dp)
        }

        // Tira de la semana
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(7) {
                SkeletonBlock(Modifier.weight(1f).height(64.dp), RoundedCornerShape(16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        // Una tarjeta de resumen (lo próximo / frase del día)
        SkeletonBlock(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(72.dp),
            RoundedCornerShape(20.dp)
        )

        // Título del día
        SkeletonLine(width = 140.dp, height = 22.dp, modifier = Modifier.padding(16.dp))

        // Tarjeta de tareas
        SkeletonBlock(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(112.dp),
            RoundedCornerShape(24.dp)
        )
        Spacer(Modifier.height(16.dp))

        // Eventos del timeline: hora, punto del raíl y tarjeta
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.width(48.dp).padding(top = 18.dp), horizontalAlignment = Alignment.End) {
                    SkeletonLine(width = 40.dp, height = 12.dp)
                }
                Box(Modifier.width(20.dp).padding(top = 19.dp), contentAlignment = Alignment.TopCenter) {
                    SkeletonCircle(10.dp)
                }
                Spacer(Modifier.width(8.dp))
                SkeletonBlock(Modifier.weight(1f).height(96.dp), RoundedCornerShape(20.dp))
            }
        }
    }
}
