package com.syncro.data.local

/**
 * Esquema exacto de la base de datos en la versión 10, copiado del código que generó Room
 * (SyncroDatabase_Impl.createAllTables) antes de subir a la versión 11.
 *
 * Sirve para los tests de migración: se crea una base de datos idéntica a la que tienen los
 * usuarios instalados y se comprueba que Room la migra sin perder datos. No se debe modificar:
 * representa lo que ya existe en los móviles.
 */
object Schema10 {
    const val VERSION = 10

    val CREATE_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `tasks` (`id` TEXT NOT NULL, `remoteId` TEXT, `taskListId` TEXT, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `date` INTEGER NOT NULL, `time` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `categoryText` TEXT, `categoryColor` INTEGER, `pendingChanges` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `user_profile` (`email` TEXT NOT NULL, `name` TEXT NOT NULL, `photoUrl` TEXT, `idToken` TEXT, PRIMARY KEY(`email`))",
        "CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `remoteId` TEXT, `title` TEXT NOT NULL, `description` TEXT, `date` INTEGER NOT NULL, `endDate` INTEGER NOT NULL DEFAULT 0, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, `categoryText` TEXT NOT NULL, `categoryColor` INTEGER NOT NULL, `priority` TEXT, `isAllDay` INTEGER NOT NULL, `location` TEXT, `notificationEnabled` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `pendingChanges` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `subtasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` TEXT NOT NULL, `title` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_subtasks_eventId` ON `subtasks` (`eventId`)",
        "CREATE TABLE IF NOT EXISTS `notes` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `color` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
    )
}
