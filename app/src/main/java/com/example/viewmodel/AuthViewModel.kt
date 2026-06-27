package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.AuthRepository
import com.example.repository.AuthRepositoryImpl
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Initial : AuthState()
    object Loading : AuthState()
    data class Success(val email: String, val displayName: String?) : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _isDemoMode = MutableStateFlow(false)
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _demoUserEmail = MutableStateFlow<String?>(null)
    val demoUserEmail: StateFlow<String?> = _demoUserEmail.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAuthState().collect { user ->
                if (user != null) {
                    _authState.value = AuthState.Success(user.email ?: "", user.displayName)
                } else if (_isDemoMode.value && _demoUserEmail.value != null) {
                    _authState.value = AuthState.Success(_demoUserEmail.value!!, "Demo Citizen")
                } else {
                    _authState.value = AuthState.Initial
                }
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState.Error("Email and Password cannot be empty.")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.login(email, password)
            result.onSuccess { user ->
                _isDemoMode.value = false
                _authState.value = AuthState.Success(user.email ?: "", user.displayName)
            }.onFailure { error ->
                // Enable demo fallback automatically if Firebase is uninitialized
                if (error.message?.contains("Firebase is not initialized") == true) {
                    _isDemoMode.value = true
                    _demoUserEmail.value = email
                    _authState.value = AuthState.Success(email, "Demo Citizen (Sandbox)")
                } else {
                    _authState.value = AuthState.Error(error.localizedMessage ?: "Login failed")
                }
            }
        }
    }

    fun register(name: String, email: String, password: String, confirmPassword: String) {
        if (name.isBlank()) {
            _authState.value = AuthState.Error("Full name cannot be empty.")
            return
        }
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }
        if (password.length < 8) {
            _authState.value = AuthState.Error("Password must be at least 8 characters.")
            return
        }
        if (confirmPassword.isBlank()) {
            _authState.value = AuthState.Error("Please confirm your password.")
            return
        }
        if (password != confirmPassword) {
            _authState.value = AuthState.Error("Passwords do not match.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.register(name, email, password)
            result.onSuccess { user ->
                _isDemoMode.value = false
                _authState.value = AuthState.Success(user.email ?: "", name)
            }.onFailure { error ->
                if (error.message?.contains("Firebase is not initialized") == true) {
                    _isDemoMode.value = true
                    _demoUserEmail.value = email
                    _authState.value = AuthState.Success(email, "$name (Sandbox)")
                } else {
                    _authState.value = AuthState.Error(error.localizedMessage ?: "Registration failed")
                }
            }
        }
    }

    fun sendPasswordReset(email: String, onSuccess: () -> Unit) {
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }

        _authState.value = AuthState.Loading
        viewModelScope.launch {
            val result = repository.sendPasswordResetEmail(email)
            result.onSuccess {
                _authState.value = AuthState.Initial
                onSuccess()
            }.onFailure { error ->
                if (error.message?.contains("Firebase is not initialized") == true) {
                    _authState.value = AuthState.Initial
                    // Simulate sandbox reset
                    onSuccess()
                } else {
                    _authState.value = AuthState.Error(error.localizedMessage ?: "Password reset failed")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _isDemoMode.value = false
            _demoUserEmail.value = null
            _authState.value = AuthState.Initial
        }
    }

    fun clearError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Initial
        }
    }
}
