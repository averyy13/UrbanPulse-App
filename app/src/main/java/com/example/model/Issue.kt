package com.example.model

data class Issue(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val imageUrl: String? = null,
    val status: String = "Pending",
    val reporterId: String = "",
    val reporterName: String = "Citizen",
    val timestamp: Long = System.currentTimeMillis(),
    val priorityScore: Int = 0,
    val votes: Int = 0,
    val upvotedBy: List<String> = emptyList(),
    val comments: List<Comment> = emptyList()
)
