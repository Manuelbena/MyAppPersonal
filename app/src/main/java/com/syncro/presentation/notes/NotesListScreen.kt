package com.syncro.presentation.notes

import com.syncro.presentation.theme.NotebookFontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.syncro.domain.model.SyncroItem
import com.syncro.presentation.components.LocalWidthClass
import com.syncro.presentation.components.WidthClass
import com.syncro.presentation.home.components.AddNoteSheet
import com.syncro.presentation.home.components.CountBadge
import com.syncro.presentation.home.components.NoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    // Se guarda el id y no la nota para que el detalle refleje los cambios al editarla
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var noteToEdit by remember { mutableStateOf<SyncroItem.Note?>(null) }
    var showNoteSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Mi libreta",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = NotebookFontFamily,
                                fontStyle = FontStyle.Italic
                            ),
                            maxLines = 1
                        )
                        if (notes.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            CountBadge(notes.size)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (notes.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        noteToEdit = null
                        showNoteSheet = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Nueva nota", fontWeight = FontWeight.Bold) },
                    shape = RoundedCornerShape(18.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (notes.isEmpty()) {
            EmptyNotebook(
                onCreate = {
                    noteToEdit = null
                    showNoteSheet = true
                },
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalStaggeredGrid(
                // Dos columnas en el móvil; en tablet, tres o cuatro para no estirar las notas
                columns = StaggeredGridCells.Fixed(
                    when (LocalWidthClass.current) {
                        WidthClass.Compact -> 2
                        WidthClass.Medium -> 3
                        WidthClass.Expanded -> 4
                    }
                ),
                modifier = Modifier.fillMaxSize().padding(padding),
                // Hueco abajo para que el botón flotante no tape la última fila
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { selectedNoteId = note.id },
                        modifier = Modifier.fillMaxWidth(),
                        contentMaxLines = 8
                    )
                }
            }
        }
    }

    selectedNoteId
        ?.let { id -> notes.firstOrNull { it.id == id } }
        ?.let { note ->
            NoteDetailSheet(
                note = note,
                onDismiss = { selectedNoteId = null },
                onEdit = {
                    selectedNoteId = null
                    noteToEdit = note
                    showNoteSheet = true
                },
                onDelete = {
                    selectedNoteId = null
                    viewModel.deleteNote(note)
                }
            )
        }

    if (showNoteSheet) {
        AddNoteSheet(
            initialNote = noteToEdit,
            onDismiss = {
                showNoteSheet = false
                noteToEdit = null
            },
            onSave = { id, title, content, color ->
                viewModel.saveNote(id, title, content, color)
                showNoteSheet = false
                noteToEdit = null
            }
        )
    }
}

@Composable
private fun EmptyNotebook(onCreate: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Tu libreta está vacía",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = NotebookFontFamily, fontStyle = FontStyle.Italic),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Apunta ideas, listas o lo que no quieras olvidar.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreate, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Crear nota", fontWeight = FontWeight.Bold)
        }
    }
}
