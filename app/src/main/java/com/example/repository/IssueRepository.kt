package com.example.repository

import android.util.Log
import com.example.model.Comment
import com.example.model.Issue
import com.example.model.Report
import com.example.model.Vote
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

interface IssueRepository {
    fun getIssues(): Flow<List<Issue>>
    suspend fun addIssue(issue: Issue): Result<Unit>
    suspend fun updateIssue(issue: Issue): Result<Unit>
    suspend fun deleteIssue(issueId: String): Result<Unit>
    suspend fun upvoteIssue(issueId: String, userId: String): Result<Unit>
    suspend fun addComment(issueId: String, comment: Comment): Result<Unit>
    suspend fun getIssueById(issueId: String): Issue?
    suspend fun deleteComment(commentId: String, reportId: String): Result<Unit>
    suspend fun updateComment(comment: Comment): Result<Unit>
    suspend fun hasUserVoted(reportId: String, userId: String): Result<Boolean>
}

class IssueRepositoryImpl(
    private val reportRepo: ReportRepository = ReportRepositoryImpl(),
    private val commentRepo: CommentRepository = CommentRepositoryImpl(),
    private val voteRepo: VoteRepository = VoteRepositoryImpl()
) : IssueRepository {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val _localIssues = MutableStateFlow<List<Issue>>(createMockIssues())
    private val localIssues = _localIssues.asStateFlow()

    init {
        val db = firestore
        if (db != null) {
            try {
                // Real-time observation of reports collection
                db.collection("reports")
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            val remoteReports = snapshot.toObjects(Report::class.java)
                            if (remoteReports.isNotEmpty()) {
                                val currentList = _localIssues.value
                                // Map reports to Issues
                                val mappedIssues = remoteReports.map { report ->
                                    val existingIssue = currentList.find { it.id == report.reportId }
                                    val mapped = report.toIssue()
                                    if (existingIssue != null) {
                                        mapped.copy(
                                            comments = existingIssue.comments,
                                            upvotedBy = existingIssue.upvotedBy
                                        )
                                    } else {
                                        mapped
                                    }
                                }
                                _localIssues.value = mappedIssues
                            }
                        }
                    }
            } catch (e: Exception) {
                // Ignore initialization failures in sandbox
            }
        }
    }

    override fun getIssues(): Flow<List<Issue>> {
        return localIssues
    }

    override suspend fun getIssueById(issueId: String): Issue? {
        val local = _localIssues.value.find { it.id == issueId }
        
        // Try to fetch fresh details including real-time comments and upvoters from Firestore
        try {
            val reportResult = reportRepo.getReport(issueId).getOrNull()
            if (reportResult != null) {
                val commentsResult = commentRepo.getCommentsForReport(issueId).getOrDefault(emptyList())
                val votesResult = voteRepo.getVotesForReport(issueId).getOrDefault(emptyList())
                return reportResult.toIssue(commentsResult, votesResult)
            }
        } catch (e: Exception) {
            Log.e("IssueRepository", "Failed to fetch remote issue: ${e.message}")
        }
        
        return local
    }

    // Prepared upload helper so it can later be replaced with a FastAPI upload endpoint
    suspend fun uploadImageToFastAPI(localUriString: String): Result<String> {
        Log.d("IssueRepository", "FastAPI placeholder: Uploading image from local URI: $localUriString")
        // Currently returns the local URI for the prototype
        return Result.success(localUriString)
    }

    override suspend fun addIssue(issue: Issue): Result<Unit> {
        val imageUrlToUse = if (!issue.imageUrl.isNullOrEmpty()) {
            uploadImageToFastAPI(issue.imageUrl).getOrDefault(issue.imageUrl)
        } else {
            null
        }

        val issueWithUploadedImage = issue.copy(imageUrl = imageUrlToUse)
        val issueWithId = if (issueWithUploadedImage.id.isEmpty()) {
            issueWithUploadedImage.copy(id = "issue_${System.currentTimeMillis()}")
        } else {
            issueWithUploadedImage
        }
        
        // Update local state immediately for instant feedback
        val updatedList = _localIssues.value.toMutableList()
        updatedList.add(0, issueWithId)
        _localIssues.value = updatedList

        try {
            val report = issueWithId.toReport()
            reportRepo.createReport(report).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore write failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun updateIssue(issue: Issue): Result<Unit> {
        val updatedList = _localIssues.value.map { if (it.id == issue.id) issue else it }
        _localIssues.value = updatedList
        try {
            reportRepo.updateReport(issue.toReport()).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore update failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun deleteIssue(issueId: String): Result<Unit> {
        val updatedList = _localIssues.value.filter { it.id != issueId }
        _localIssues.value = updatedList
        try {
            reportRepo.deleteReport(issueId).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore delete failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun upvoteIssue(issueId: String, userId: String): Result<Unit> {
        val originalList = _localIssues.value
        val updatedList = originalList.map { issue ->
            if (issue.id == issueId) {
                val alreadyVoted = issue.upvotedBy.contains(userId)
                val newUpvotedBy = if (alreadyVoted) issue.upvotedBy else issue.upvotedBy + userId
                val newVotes = if (alreadyVoted) issue.votes else issue.votes + 1
                issue.copy(
                    upvotedBy = newUpvotedBy,
                    votes = newVotes
                )
            } else {
                issue
            }
        }
        _localIssues.value = updatedList

        try {
            voteRepo.addVote(Vote(userId = userId, reportId = issueId)).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore upvote failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun addComment(issueId: String, comment: Comment): Result<Unit> {
        val commentWithId = if (comment.id.isEmpty()) {
            val generatedId = "comment_${System.currentTimeMillis()}"
            comment.copy(id = generatedId, commentId = generatedId)
        } else {
            comment
        }
        
        val normalizedComment = commentWithId.copy(
            commentText = if (commentWithId.commentText.isEmpty()) commentWithId.comment else commentWithId.commentText,
            comment = if (commentWithId.comment.isEmpty()) commentWithId.commentText else commentWithId.comment,
            createdAt = if (commentWithId.createdAt <= 0) commentWithId.timestamp else commentWithId.createdAt,
            timestamp = if (commentWithId.timestamp <= 0) commentWithId.createdAt else commentWithId.timestamp
        )

        val originalList = _localIssues.value
        val updatedList = originalList.map { issue ->
            if (issue.id == issueId) {
                val alreadyExists = issue.comments.any { it.id == normalizedComment.id }
                val newComments = if (alreadyExists) issue.comments else issue.comments + normalizedComment
                issue.copy(comments = newComments)
            } else {
                issue
            }
        }
        _localIssues.value = updatedList

        try {
            val firestoreComment = Comment(
                id = normalizedComment.id,
                commentId = normalizedComment.id,
                reportId = issueId,
                userId = normalizedComment.userId,
                comment = normalizedComment.comment,
                commentText = normalizedComment.commentText,
                createdAt = normalizedComment.createdAt,
                timestamp = normalizedComment.timestamp,
                username = normalizedComment.username
            )
            commentRepo.addComment(firestoreComment).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore comment failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun deleteComment(commentId: String, reportId: String): Result<Unit> {
        val originalList = _localIssues.value
        val updatedList = originalList.map { issue ->
            if (issue.id == reportId) {
                issue.copy(
                    comments = issue.comments.filter { it.id != commentId }
                )
            } else {
                issue
            }
        }
        _localIssues.value = updatedList

        try {
            commentRepo.deleteComment(commentId).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore delete comment failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun updateComment(comment: Comment): Result<Unit> {
        val normalizedComment = comment.copy(
            commentText = if (comment.commentText.isEmpty()) comment.comment else comment.commentText,
            comment = if (comment.comment.isEmpty()) comment.commentText else comment.comment,
            createdAt = if (comment.createdAt <= 0) comment.timestamp else comment.createdAt,
            timestamp = if (comment.timestamp <= 0) comment.createdAt else comment.timestamp
        )

        val originalList = _localIssues.value
        val updatedList = originalList.map { issue ->
            if (issue.id == normalizedComment.reportId) {
                val newComments = issue.comments.map { if (it.id == normalizedComment.id) normalizedComment else it }
                issue.copy(comments = newComments)
            } else {
                issue
            }
        }
        _localIssues.value = updatedList

        try {
            val firestoreComment = Comment(
                id = normalizedComment.id,
                commentId = normalizedComment.id,
                reportId = normalizedComment.reportId,
                userId = normalizedComment.userId,
                comment = normalizedComment.comment,
                commentText = normalizedComment.commentText,
                createdAt = normalizedComment.createdAt,
                timestamp = normalizedComment.timestamp,
                username = normalizedComment.username
            )
            commentRepo.updateComment(firestoreComment).getOrThrow()
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e("IssueRepository", "Firestore update comment failed, using local state: ${e.message}")
            return Result.success(Unit)
        }
    }

    override suspend fun hasUserVoted(reportId: String, userId: String): Result<Boolean> {
        val localVoted = _localIssues.value.find { it.id == reportId }?.upvotedBy?.contains(userId) ?: false
        if (localVoted) return Result.success(true)
        
        return try {
            voteRepo.hasUserVoted(reportId, userId)
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    // --- Mapper Helpers ---

    private fun priorityToScore(priority: String): Int {
        return when (priority.lowercase()) {
            "critical" -> 3
            "high" -> 2
            "medium" -> 1
            else -> 0
        }
    }

    private fun scoreToPriority(score: Int): String {
        return when {
            score >= 3 -> "Critical"
            score == 2 -> "High"
            score == 1 -> "Medium"
            else -> "Low"
        }
    }

    private fun Report.toIssue(comments: List<Comment> = emptyList(), votes: List<Vote> = emptyList()): Issue {
        val mappedComments = comments.map { c ->
            val textToUse = if (c.commentText.isNotEmpty()) c.commentText else c.comment
            val timeToUse = if (c.timestamp > 0) c.timestamp else if (c.createdAt > 0) c.createdAt else System.currentTimeMillis()
            Comment(
                id = if (c.id.isNotEmpty()) c.id else if (c.commentId.isNotEmpty()) c.commentId else "comment_${System.currentTimeMillis()}",
                commentId = if (c.commentId.isNotEmpty()) c.commentId else if (c.id.isNotEmpty()) c.id else "comment_${System.currentTimeMillis()}",
                reportId = c.reportId,
                userId = c.userId,
                comment = textToUse,
                commentText = textToUse,
                createdAt = timeToUse,
                timestamp = timeToUse,
                username = c.username
            )
        }
        return Issue(
            id = this.reportId,
            title = if (this.category.isNotEmpty()) "${this.category} Incident" else "Urban Report",
            description = this.description,
            category = this.category,
            latitude = this.latitude,
            longitude = this.longitude,
            address = this.address,
            imageUrl = this.localImageUri ?: this.imageUrl,
            status = this.status,
            reporterId = this.userId,
            reporterName = "Citizen",
            timestamp = this.createdAt,
            priorityScore = priorityToScore(this.priority),
            votes = this.voteCount,
            upvotedBy = votes.map { it.userId },
            comments = mappedComments
        )
    }

    private fun Issue.toReport(): Report {
        return Report(
            reportId = this.id,
            userId = this.reporterId,
            category = this.category,
            description = this.description,
            imageUrl = this.imageUrl,
            localImageUri = this.imageUrl,
            latitude = this.latitude,
            longitude = this.longitude,
            address = this.address,
            priority = scoreToPriority(this.priorityScore),
            status = this.status,
            voteCount = this.votes,
            commentCount = this.comments.size,
            createdAt = this.timestamp,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createMockIssues(): List<Issue> {
        return listOf(
            Issue(
                id = "issue_mock_1",
                title = "Deep Dangerous Pothole",
                description = "Massive 10-inch deep pothole in the middle of the road, causing cars to swerve dangerously into oncoming traffic.",
                category = "Pothole",
                latitude = 37.7749,
                longitude = -122.4194,
                imageUrl = "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?auto=format&fit=crop&q=80&w=400",
                status = "Pending",
                reporterId = "reporter_mock_1",
                reporterName = "Marcus Aurelius",
                timestamp = System.currentTimeMillis() - 7200000,
                priorityScore = 3,
                votes = 12,
                upvotedBy = listOf("u1", "u2", "u3"),
                comments = listOf(
                    Comment(id = "c1", userId = "user1", username = "Officer Smith", commentText = "Sewer repair crew has been notified.", timestamp = System.currentTimeMillis() - 3600000),
                    Comment(id = "c2", userId = "user2", username = "Citizen Jane", commentText = "Nearly popped my front tire here this morning!", timestamp = System.currentTimeMillis() - 1800000)
                )
            ),
            Issue(
                id = "issue_mock_2",
                title = "Broken Streetlight",
                description = "The main streetlight at the park entryway intersection is completely blacked out, making it very hazardous at night.",
                category = "Light Out",
                latitude = 37.7833,
                longitude = -122.4167,
                imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&q=80&w=400",
                status = "Reviewing",
                reporterId = "reporter_mock_2",
                reporterName = "Serena Williams",
                timestamp = System.currentTimeMillis() - 86400000,
                priorityScore = 1,
                votes = 5,
                upvotedBy = listOf("u1", "u2"),
                comments = emptyList()
            ),
            Issue(
                id = "issue_mock_3",
                title = "Illegal Toxic Waste Dump",
                description = "Several canisters of paint and automotive fluid bottles dumped on the side of Greenway Park walking trail.",
                category = "Waste",
                latitude = 37.7694,
                longitude = -122.4417,
                imageUrl = "https://images.unsplash.com/photo-1611284446314-60a58ac0deb9?auto=format&fit=crop&q=80&w=400",
                status = "Fixed",
                reporterId = "reporter_mock_3",
                reporterName = "Keanu Reeves",
                timestamp = System.currentTimeMillis() - 259200000,
                priorityScore = 2,
                votes = 24,
                upvotedBy = listOf("u4", "u5", "u6"),
                comments = listOf(
                    Comment(id = "c3", userId = "city_admin", username = "Admin Sarah", commentText = "Sanitation department has fully cleared this site.", timestamp = System.currentTimeMillis() - 86400000)
                )
            ),
            Issue(
                id = "issue_mock_4",
                title = "Burst Water Pipeline",
                description = "Fresh drinking water is gushing out from the sidewalk pavement constantly, flooding the local bicycle path.",
                category = "Water Leak",
                latitude = 37.7599,
                longitude = -122.4367,
                imageUrl = "https://images.unsplash.com/photo-1504328345606-18bbc8c9d7d1?auto=format&fit=crop&q=80&w=400",
                status = "Pending",
                reporterId = "reporter_mock_4",
                reporterName = "Diana Prince",
                timestamp = System.currentTimeMillis() - 172800000,
                priorityScore = 3,
                votes = 8,
                upvotedBy = emptyList(),
                comments = emptyList()
            )
        )
    }
}
