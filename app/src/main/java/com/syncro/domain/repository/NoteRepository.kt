package com.syncro.domain.repository

import com.syncro.domain.model.SyncroItem
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<List<SyncroItem.Note>>
    suspend fun insertNote(note: SyncroItem.Note)
    suspend fun deleteNote(id: String)
    suspend fun getNoteById(id: String): SyncroItem.Note?
}
