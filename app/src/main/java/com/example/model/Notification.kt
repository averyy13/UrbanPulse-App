package com.example.model

data class Notification(
    val notificationId: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "General",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
