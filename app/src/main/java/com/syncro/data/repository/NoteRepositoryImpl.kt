package com.syncro.data.repository

import com.syncro.domain.model.ArgbColor
import com.syncro.data.local.dao.NoteDao
import com.syncro.data.local.entity.NoteEntity
import com.syncro.domain.model.SyncroItem
import com.syncro.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao
) : NoteRepository {

    override fun getAllNotes(): Flow<List<SyncroItem.Note>> {
        return dao.getAllNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertNote(note: SyncroItem.Note) {
        dao.insertNote(
            NoteEntity(
                id = note.id,
                title = note.title,
                content = note.content,
                color = note.color.argb,
                createdAt = note.createdAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        )
    }

    override suspend fun deleteNote(note: SyncroItem.Note) {
        dao.deleteNoteById(note.id)
    }

    override suspend fun getNoteById(id: String): SyncroItem.Note? {
        return dao.getNoteById(id)?.toDomain()
    }

    private fun NoteEntity.toDomain(): SyncroItem.Note {
        return SyncroItem.Note(
            id = id,
            title = title,
            content = content,
            color = ArgbColor(color),
            createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault())
        )
    }
}
