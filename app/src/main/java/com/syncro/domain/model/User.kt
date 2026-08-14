package com.syncro.domain.model

data class User(
    val email: String,
    val name: String,
    val photoUrl: String?,
    val idToken: String? = null
)
