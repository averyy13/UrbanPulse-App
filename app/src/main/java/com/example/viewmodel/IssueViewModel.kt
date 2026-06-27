package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Comment
import com.example.model.Issue
import com.example.repository.IssueRepository
import com.example.repository.IssueRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IssueViewModel(
    private val repository: IssueRepository = IssueRepositoryImpl()
) : ViewModel() {

    private val _issues = MutableStateFlow<List<Issue>>(emptyList())
    val issues: StateFlow<List<Issue>> = _issues.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedIssue = MutableStateFlow<Issue?>(null)
    val selectedIssue: StateFlow<Issue?> = _selectedIssue.asStateFlow()

    init {
        loadIssues()
    }

    private fun loadIssues() {
        viewModelScope.launch {
            repository.getIssues().collect { result ->
                _issues.value = result
                val currentSelected = _selectedIssue.value
                if (currentSelected != null) {
                    val updated = result.find { it.id == currentSelected.id }
                    if (updated != null) {
                        _selectedIssue.value = updated
                    }
                }
            }
        }
    }

    fun selectIssue(issueId: String) {
        viewModelScope.launch {
            _selectedIssue.value = repository.getIssueById(issueId)
        }
    }

    fun addIssue(issue: Issue, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isUploading.value = true
            val result = repository.addIssue(issue)
            _isUploading.value = false
            onComplete(result)
        }
    }

    fun updateIssue(issue: Issue, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isUploading.value = true
            val result = repository.updateIssue(issue)
            if (_selectedIssue.value?.id == issue.id) {
                _selectedIssue.value = issue
            }
            _isUploading.value = false
            onComplete(result)
        }
    }

    fun deleteIssue(issueId: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            _isUploading.value = true
            val result = repository.deleteIssue(issueId)
            if (_selectedIssue.value?.id == issueId) {
                _selectedIssue.value = null
            }
            _isUploading.value = false
            onComplete(result)
        }
    }

    fun upvoteIssue(issueId: String, userId: String, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.upvoteIssue(issueId, userId)
            // Refresh selected issue if it is currently displayed
            if (_selectedIssue.value?.id == issueId) {
                _selectedIssue.value = repository.getIssueById(issueId)
            }
            onComplete(result)
        }
    }

    fun addComment(issueId: String, comment: Comment, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.addComment(issueId, comment)
            // Refresh selected issue if it is currently displayed
            if (_selectedIssue.value?.id == issueId) {
                _selectedIssue.value = repository.getIssueById(issueId)
            }
            onComplete(result)
        }
    }

    fun deleteComment(commentId: String, reportId: String, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.deleteComment(commentId, reportId)
            if (_selectedIssue.value?.id == reportId) {
                _selectedIssue.value = repository.getIssueById(reportId)
            }
            onComplete(result)
        }
    }

    fun updateComment(comment: Comment, onComplete: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.updateComment(comment)
            if (_selectedIssue.value?.id == comment.reportId) {
                _selectedIssue.value = repository.getIssueById(comment.reportId)
            }
            onComplete(result)
        }
    }

    fun checkUserVote(reportId: String, userId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.hasUserVoted(reportId, userId).getOrDefault(false)
            onResult(result)
        }
    }

    fun refreshIssues() {
        viewModelScope.launch {
            _isRefreshing.value = true
            // Simulate networking delay
            kotlinx.coroutines.delay(1000)
            _isRefreshing.value = false
        }
    }
}
