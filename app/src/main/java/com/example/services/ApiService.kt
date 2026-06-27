package com.example.services

import com.example.model.Issue
import retrofit2.http.GET

interface ApiService {
    @GET("issues")
    suspend fun getIssues(): List<Issue>
}
