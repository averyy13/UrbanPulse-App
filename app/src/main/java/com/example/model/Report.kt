package com.example.model

data class Report(
    val reportId: String = "",
    val userId: String = "",
    val category: String = "",
    val description: String = "",
    val imageUrl: String? = null,
    val localImageUri: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val priority: String = "Medium", // Low, Medium, High, Critical
    val status: String = "Pending", // Pending, In Progress, Resolved
    val voteCount: Int = 0,
    val commentCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
