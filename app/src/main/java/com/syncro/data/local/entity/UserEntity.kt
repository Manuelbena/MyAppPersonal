package com.syncro.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserEntity(
    @PrimaryKey val email: String,
    val name: String,
    val photoUrl: String?,
    val idToken: String?
)
