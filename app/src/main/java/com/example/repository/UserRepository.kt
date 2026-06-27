package com.example.repository

import com.example.model.User
import com.example.services.FirestoreService

interface UserRepository {
    suspend fun createUser(user: User): Result<Unit>
    suspend fun getUser(uid: String): Result<User?>
    suspend fun updateUser(user: User): Result<Unit>
    suspend fun incrementUserReportCount(uid: String): Result<Unit>
}

class UserRepositoryImpl(
    private val firestoreService: FirestoreService = FirestoreService()
) : UserRepository {
    override suspend fun createUser(user: User): Result<Unit> = firestoreService.createUser(user)
    override suspend fun getUser(uid: String): Result<User?> = firestoreService.getUser(uid)
    override suspend fun updateUser(user: User): Result<Unit> = firestoreService.updateUser(user)
    override suspend fun incrementUserReportCount(uid: String): Result<Unit> = firestoreService.incrementUserReportCount(uid)
}
