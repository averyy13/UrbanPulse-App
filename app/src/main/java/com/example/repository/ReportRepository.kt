package com.example.repository

import com.example.model.Report
import com.example.services.FirestoreService

interface ReportRepository {
    suspend fun createReport(report: Report): Result<String>
    suspend fun getReport(reportId: String): Result<Report?>
    suspend fun getReports(): Result<List<Report>>
    suspend fun updateReport(report: Report): Result<Unit>
    suspend fun deleteReport(reportId: String): Result<Unit>
}

class ReportRepositoryImpl(
    private val firestoreService: FirestoreService = FirestoreService()
) : ReportRepository {
    override suspend fun createReport(report: Report): Result<String> = firestoreService.createReport(report)
    override suspend fun getReport(reportId: String): Result<Report?> = firestoreService.getReport(reportId)
    override suspend fun getReports(): Result<List<Report>> = firestoreService.getReports()
    override suspend fun updateReport(report: Report): Result<Unit> = firestoreService.updateReport(report)
    override suspend fun deleteReport(reportId: String): Result<Unit> = firestoreService.deleteReport(reportId)
}
