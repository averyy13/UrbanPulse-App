package com.example.model

data class Comment(
    val commentId: String = "",
    val reportId: String = "",
    val userId: String = "",
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val username: String = "Citizen",
    val id: String = "",
    val commentText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
