package com.syncro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.syncro.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    @Query("DELETE FROM user_profile")
    suspend fun deleteUser()

    /** Solo puede haber una sesión: la cuenta nueva sustituye a la anterior en vez de convivir con ella. */
    @Transaction
    suspend fun replaceUser(user: UserEntity) {
        deleteUser()
        saveUser(user)
    }
}
