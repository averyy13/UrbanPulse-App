package com.example.repository

import com.example.model.Notification
import com.example.services.FirestoreService

interface NotificationRepository {
    suspend fun createNotification(notification: Notification): Result<String>
    suspend fun getNotificationsForUser(userId: String): Result<List<Notification>>
    suspend fun markNotificationAsRead(notificationId: String): Result<Unit>
}

class NotificationRepositoryImpl(
    private val firestoreService: FirestoreService = FirestoreService()
) : NotificationRepository {
    override suspend fun createNotification(notification: Notification): Result<String> = firestoreService.createNotification(notification)
    override suspend fun getNotificationsForUser(userId: String): Result<List<Notification>> = firestoreService.getNotificationsForUser(userId)
    override suspend fun markNotificationAsRead(notificationId: String): Result<Unit> = firestoreService.markNotificationAsRead(notificationId)
}
