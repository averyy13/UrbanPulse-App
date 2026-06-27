package com.example.repository

import com.example.model.Vote
import com.example.services.FirestoreService

interface VoteRepository {
    suspend fun addVote(vote: Vote): Result<String>
    suspend fun removeVote(reportId: String, userId: String): Result<Unit>
    suspend fun getVotesForReport(reportId: String): Result<List<Vote>>
    suspend fun hasUserVoted(reportId: String, userId: String): Result<Boolean>
}

class VoteRepositoryImpl(
    private val firestoreService: FirestoreService = FirestoreService()
) : VoteRepository {
    override suspend fun addVote(vote: Vote): Result<String> = firestoreService.addVote(vote)
    override suspend fun removeVote(reportId: String, userId: String): Result<Unit> = firestoreService.removeVote(reportId, userId)
    override suspend fun getVotesForReport(reportId: String): Result<List<Vote>> = firestoreService.getVotesForReport(reportId)
    override suspend fun hasUserVoted(reportId: String, userId: String): Result<Boolean> = firestoreService.hasUserVoted(reportId, userId)
}
