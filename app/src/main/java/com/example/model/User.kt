package com.example.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val phoneNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val reportCount: Int = 0
)
