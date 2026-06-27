package com.example.model

data class Vote(
    val voteId: String = "",
    val reportId: String = "",
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
