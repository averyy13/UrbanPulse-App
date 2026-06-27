package com.example.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val currentUser: FirebaseUser?
    fun observeAuthState(): StateFlow<FirebaseUser?>
    suspend fun login(email: String, password: String): Result<FirebaseUser>
    suspend fun register(name: String, email: String, password: String): Result<FirebaseUser>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun logout(): Result<Unit>
}

class AuthRepositoryImpl(
    private val userRepository: UserRepository = UserRepositoryImpl()
) : AuthRepository {

    private val firebaseAuth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val _authState = MutableStateFlow<FirebaseUser?>(null)

    init {
        val auth = firebaseAuth
        if (auth != null) {
            _authState.value = auth.currentUser
            auth.addAuthStateListener { listenerAuth ->
                _authState.value = listenerAuth.currentUser
            }
        }
    }

    override val currentUser: FirebaseUser?
        get() = firebaseAuth?.currentUser

    override fun observeAuthState(): StateFlow<FirebaseUser?> = _authState

    override suspend fun login(email: String, password: String): Result<FirebaseUser> {
        val auth = firebaseAuth ?: return Result.failure(Exception("Firebase is not initialized. Please add google-services.json to configure Firebase Authentication."))
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to retrieve user after login"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(name: String, email: String, password: String): Result<FirebaseUser> {
        val auth = firebaseAuth ?: return Result.failure(Exception("Firebase is not initialized. Please add google-services.json to configure Firebase Authentication."))
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                // Set the display name in Firebase Auth
                try {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (e: Exception) {
                    // Profile update is non-critical, we log and proceed
                    android.util.Log.e("AuthRepository", "Failed to update profile name: ${e.message}")
                }

                // Create the user profile document in Firestore
                val newUser = com.example.model.User(
                    uid = user.uid,
                    fullName = name,
                    email = email,
                    createdAt = System.currentTimeMillis(),
                    reportCount = 0
                )
                val firestoreResult = userRepository.createUser(newUser)
                if (firestoreResult.isSuccess) {
                    Result.success(user)
                } else {
                    // Firestore failed, clean up Firebase Authentication user to avoid orphaned account
                    try {
                        user.delete().await()
                    } catch (deleteError: Exception) {
                        android.util.Log.e("AuthRepository", "Failed to delete Firebase User after Firestore write failed: ${deleteError.message}")
                    }
                    Result.failure(firestoreResult.exceptionOrNull() ?: Exception("Failed to create Firestore profile for user."))
                }
            } else {
                Result.failure(Exception("Failed to retrieve user after registration"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val auth = firebaseAuth ?: return Result.failure(Exception("Firebase is not initialized. Please add google-services.json to configure Firebase Authentication."))
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        val auth = firebaseAuth ?: return Result.success(Unit) // If null, we are already effectively signed out
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
