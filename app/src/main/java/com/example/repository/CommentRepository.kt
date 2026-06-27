package com.example.repository

import com.example.model.Comment
import com.example.services.FirestoreService

interface CommentRepository {
    suspend fun addComment(comment: Comment): Result<String>
    suspend fun getCommentsForReport(reportId: String): Result<List<Comment>>
    suspend fun deleteComment(commentId: String): Result<Unit>
    suspend fun updateComment(comment: Comment): Result<Unit>
}

class CommentRepositoryImpl(
    private val firestoreService: FirestoreService = FirestoreService()
) : CommentRepository {
    override suspend fun addComment(comment: Comment): Result<String> = firestoreService.addComment(comment)
    override suspend fun getCommentsForReport(reportId: String): Result<List<Comment>> = firestoreService.getCommentsForReport(reportId)
    override suspend fun deleteComment(commentId: String): Result<Unit> = firestoreService.deleteComment(commentId)
    override suspend fun updateComment(comment: Comment): Result<Unit> = firestoreService.updateComment(comment)
}
