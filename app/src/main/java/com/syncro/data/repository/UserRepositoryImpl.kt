package com.syncro.data.repository

import com.syncro.data.local.dao.UserDao
import com.syncro.data.local.entity.UserEntity
import com.syncro.domain.model.User
import com.syncro.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override fun getUser(): Flow<User?> {
        return userDao.getUser().map { it?.toDomain() }
    }

    override suspend fun saveUser(user: User) {
        userDao.replaceUser(
            UserEntity(
                email = user.email,
                name = user.name,
                photoUrl = user.photoUrl,
                idToken = user.idToken
            )
        )
    }

    override suspend fun clearUser() {
        userDao.deleteUser()
    }

    private fun UserEntity.toDomain() = User(
        email = email,
        name = name,
        photoUrl = photoUrl,
        idToken = idToken
    )
}
