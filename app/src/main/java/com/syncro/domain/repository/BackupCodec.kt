package com.syncro.domain.repository

import com.syncro.domain.model.BackupContent

/** Convierte una copia de seguridad en texto (el archivo) y al revés. */
interface BackupCodec {
    fun encode(content: BackupContent): String
    /** Lanza [com.syncro.domain.model.InvalidBackupException] si el texto no es una copia válida. */
    fun decode(text: String): BackupContent
}
