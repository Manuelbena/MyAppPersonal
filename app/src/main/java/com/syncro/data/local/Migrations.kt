package com.syncro.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Añade la lista de Google Tasks a la que pertenece cada tarea (antes se asumía @default). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN taskListId TEXT")
    }
}

/** Cambios locales pendientes de subir a Google, para no perderlos sin conexión. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE events ADD COLUMN pendingChanges INTEGER NOT NULL DEFAULT 0")
    }
}
