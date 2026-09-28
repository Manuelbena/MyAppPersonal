package com.syncro.presentation.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.syncro.presentation.components.SyncroIconButton

/** Un documento legal a pantalla completa. Se abre desde Ajustes y desde el inicio de sesión. */
@Composable
fun LegalDocumentScreen(documentId: LegalDocumentId, onBack: () -> Unit) {
    val document = remember(documentId) { legalDocument(documentId) }
    var openLicense by remember { mutableStateOf<OpenSourceComponent?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SyncroIconButton(icon = Icons.AutoMirrored.Outlined.ArrowBack, onClick = onBack, contentDescription = "Volver")
            Spacer(Modifier.width(16.dp))
            Text(
                documentId.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            Text(document.intro, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            document.sections.forEach { section ->
                Text(
                    section.heading,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                section.paragraphs.forEach { paragraph ->
                    val bullet = paragraph.startsWith("• ")
                    Row(modifier = Modifier.padding(bottom = 8.dp, start = if (bullet) 4.dp else 0.dp)) {
                        if (bullet) {
                            Text("•", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            paragraph.removePrefix("• "),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (documentId == LegalDocumentId.OPEN_SOURCE) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ) {
                    Column {
                        openSourceComponents.forEachIndexed { index, component ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) { openLicense = component }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(component.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${component.author} · ${component.license}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    openLicense?.let { component ->
        LicenseTextDialog(component = component, onDismiss = { openLicense = null })
    }
}

/** El texto completo de una licencia, tal cual (en inglés, que es el que tiene validez). */
@Composable
private fun LicenseTextDialog(component: OpenSourceComponent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(component) {
        runCatching { context.assets.open(component.licenseFile).bufferedReader().use { it.readText() } }
            .getOrDefault("No se pudo abrir la licencia.")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(component.license) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(component.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
