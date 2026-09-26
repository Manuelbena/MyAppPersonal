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

/**
 * Fecha de fin de los eventos, para los que cruzan la medianoche (21:30 → 01:00).
 * Los que ya existían terminan el mismo día, salvo los que tenían la hora de fin anterior a la de
 * inicio: eso solo tiene sentido si terminaban al día siguiente, y Google los rechazaba, así que
 * se corrigen para que por fin se puedan subir.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE events ADD COLUMN endDate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE events SET endDate = date")
        // Las horas son "HH:mm", así que se pueden comparar como texto
        db.execSQL("UPDATE events SET endDate = date + 1 WHERE endTime < startTime")
    }
}
