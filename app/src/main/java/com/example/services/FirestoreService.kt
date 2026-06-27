package com.example.services

import android.util.Log
import com.example.model.Comment
import com.example.model.Notification
import com.example.model.Report
import com.example.model.User
import com.example.model.Vote
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreService {

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("FirestoreService", "Failed to initialize FirebaseFirestore: ${e.message}")
            null
        }
    }

    // --- Users Collection Operations ---

    suspend fun createUser(user: User): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("users").document(user.uid).set(user).await()
    }

    suspend fun getUser(uid: String): Result<User?> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("users").document(uid).get().await()
        if (snapshot.exists()) {
            snapshot.toObject(User::class.java)
        } else {
            null
        }
    }

    suspend fun updateUser(user: User): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("users").document(user.uid).set(user).await()
    }

    suspend fun incrementUserReportCount(uid: String): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("users").document(uid)
            .update("reportCount", FieldValue.increment(1)).await()
    }

    // --- Reports Collection Operations ---

    suspend fun createReport(report: Report): Result<String> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val ref = if (report.reportId.isEmpty()) {
            db.collection("reports").document()
        } else {
            db.collection("reports").document(report.reportId)
        }
        val finalReport = report.copy(reportId = ref.id)
        ref.set(finalReport).await()
        
        // Increment reportCount for this user
        if (report.userId.isNotEmpty()) {
            incrementUserReportCount(report.userId)
        }
        
        finalReport.reportId
    }

    suspend fun getReport(reportId: String): Result<Report?> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("reports").document(reportId).get().await()
        if (snapshot.exists()) {
            snapshot.toObject(Report::class.java)
        } else {
            null
        }
    }

    suspend fun getReports(): Result<List<Report>> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get().await()
        snapshot.toObjects(Report::class.java)
    }

    suspend fun updateReport(report: Report): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("reports").document(report.reportId).set(report).await()
    }

    suspend fun deleteReport(reportId: String): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("reports").document(reportId).get().await()
        if (snapshot.exists()) {
            val report = snapshot.toObject(Report::class.java)
            db.collection("reports").document(reportId).delete().await()
            if (report != null && report.userId.isNotEmpty()) {
                db.collection("users").document(report.userId)
                    .update("reportCount", FieldValue.increment(-1)).await()
            }
        }
    }

    // --- Comments Collection Operations ---

    suspend fun addComment(comment: Comment): Result<String> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val ref = db.collection("comments").document()
        val finalComment = comment.copy(commentId = ref.id, id = ref.id)
        val reportRef = db.collection("reports").document(comment.reportId)
        
        db.runTransaction { transaction ->
            // Perform all reads first
            val reportSnapshot = transaction.get(reportRef)
            
            // Perform all writes second
            transaction.set(ref, finalComment)
            if (reportSnapshot.exists()) {
                val currentCount = reportSnapshot.getLong("commentCount") ?: 0
                transaction.update(reportRef, "commentCount", currentCount + 1)
            }
        }.await()

        finalComment.commentId
    }

    suspend fun updateComment(comment: Comment): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("comments").document(comment.commentId).set(comment).await()
    }

    suspend fun getCommentsForReport(reportId: String): Result<List<Comment>> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("comments")
            .whereEqualTo("reportId", reportId)
            .get().await()
        
        val list = snapshot.toObjects(Comment::class.java)
        list.sortedByDescending { it.createdAt }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val commentRef = db.collection("comments").document(commentId)
        val snapshot = commentRef.get().await()
        if (snapshot.exists()) {
            val comment = snapshot.toObject(Comment::class.java)
            if (comment != null && comment.reportId.isNotEmpty()) {
                val reportRef = db.collection("reports").document(comment.reportId)
                db.runTransaction { transaction ->
                    // Perform all reads first
                    val reportSnapshot = transaction.get(reportRef)
                    
                    // Perform all writes second
                    transaction.delete(commentRef)
                    if (reportSnapshot.exists()) {
                        val currentCount = reportSnapshot.getLong("commentCount") ?: 0
                        val newCount = if (currentCount > 0) currentCount - 1 else 0
                        transaction.update(reportRef, "commentCount", newCount)
                    }
                }.await()
            } else {
                commentRef.delete().await()
            }
        }
    }

    // --- Votes Collection Operations ---

    suspend fun addVote(vote: Vote): Result<String> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val voteDocId = "${vote.userId}_${vote.reportId}"
        val voteRef = db.collection("votes").document(voteDocId)
        val reportRef = db.collection("reports").document(vote.reportId)
        
        db.runTransaction { transaction ->
            // Perform all reads first
            val voteSnapshot = transaction.get(voteRef)
            val reportSnapshot = transaction.get(reportRef)
            
            if (voteSnapshot.exists()) {
                throw Exception("Already voted")
            }
            
            // Perform all writes second
            val finalVote = vote.copy(voteId = voteDocId)
            transaction.set(voteRef, finalVote)
            
            if (reportSnapshot.exists()) {
                val currentVotes = reportSnapshot.getLong("voteCount") ?: 0
                transaction.update(reportRef, "voteCount", currentVotes + 1)
            }
        }.await()
        
        voteDocId
    }

    suspend fun removeVote(reportId: String, userId: String): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val voteDocId = "${userId}_$reportId"
        db.collection("votes").document(voteDocId).delete().await()

        // Decrement vote count in report document
        db.collection("reports").document(reportId)
            .update("voteCount", FieldValue.increment(-1)).await()
    }

    suspend fun getVotesForReport(reportId: String): Result<List<Vote>> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("votes")
            .whereEqualTo("reportId", reportId)
            .get().await()
        snapshot.toObjects(Vote::class.java)
    }

    suspend fun hasUserVoted(reportId: String, userId: String): Result<Boolean> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val voteDocId = "${userId}_$reportId"
        val snapshot = db.collection("votes").document(voteDocId).get().await()
        snapshot.exists()
    }

    // --- Notifications Collection Operations ---

    suspend fun createNotification(notification: Notification): Result<String> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val ref = db.collection("notifications").document()
        val finalNotification = notification.copy(notificationId = ref.id)
        ref.set(finalNotification).await()
        finalNotification.notificationId
    }

    suspend fun getNotificationsForUser(userId: String): Result<List<Notification>> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        val snapshot = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .get().await()
        val list = snapshot.toObjects(Notification::class.java)
        list.sortedByDescending { it.createdAt }
    }

    suspend fun markNotificationAsRead(notificationId: String): Result<Unit> = runCatching {
        val db = firestore ?: throw Exception("Firestore is not initialized")
        db.collection("notifications").document(notificationId)
            .update("isRead", true).await()
    }
}
