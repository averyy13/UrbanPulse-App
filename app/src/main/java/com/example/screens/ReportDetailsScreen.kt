package com.example.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.model.Comment
import com.example.model.Issue
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.IssueViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailsScreen(
    issueId: String,
    onNavigateBack: () -> Unit,
    issueViewModel: IssueViewModel,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val selectedIssue by issueViewModel.selectedIssue.collectAsState()
    val authState by authViewModel.authState.collectAsState()

    val currentUserId = if (authState is AuthState.Success) (authState as AuthState.Success).email else "anonymous"
    val currentUserName = if (authState is AuthState.Success) {
        (authState as AuthState.Success).displayName ?: "Active Citizen"
    } else {
        "Active Citizen"
    }

    var commentText by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editDescription by remember { mutableStateOf("") }
    var editCategory by remember { mutableStateOf("") }

    // Voting and comment interaction states
    var userHasVotedState by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<Comment?>(null) }
    var editingCommentText by remember { mutableStateOf("") }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }

    // Fetch details and voting state on enter
    LaunchedEffect(issueId, currentUserId) {
        issueViewModel.selectIssue(issueId)
        if (currentUserId.isNotEmpty() && currentUserId != "anonymous") {
            issueViewModel.checkUserVote(issueId, currentUserId) { voted ->
                userHasVotedState = voted
            }
        }
    }

    var isSubmittingComment by remember { mutableStateOf(false) }

    // Comment Editing Dialog
    if (editingComment != null) {
        AlertDialog(
            onDismissRequest = { editingComment = null },
            title = { Text("Edit Comment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = editingCommentText,
                        onValueChange = { 
                            if (it.length <= 300) {
                                editingCommentText = it 
                            }
                        },
                        placeholder = { Text("Update your comment...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )
                    Text(
                        text = "${editingCommentText.length} / 300 characters",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (editingCommentText.length > 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editingCommentText.isNotBlank()) {
                            val commentToUpdate = editingComment!!.copy(
                                comment = editingCommentText.trim(),
                                commentText = editingCommentText.trim()
                            )
                            issueViewModel.updateComment(commentToUpdate) { result ->
                                result.onSuccess {
                                    Toast.makeText(context, "Comment updated!", Toast.LENGTH_SHORT).show()
                                    editingComment = null
                                }.onFailure { error ->
                                    Toast.makeText(context, "Error updating comment: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = editingCommentText.isNotBlank() && editingCommentText.length <= 300
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingComment = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Comment Deletion Confirmation Dialog
    if (commentToDelete != null) {
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text("Delete Comment") },
            text = { Text("Are you sure you want to delete this comment? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val commentId = commentToDelete!!.id
                        val reportId = selectedIssue?.id ?: issueId
                        issueViewModel.deleteComment(commentId, reportId) { result ->
                            result.onSuccess {
                                Toast.makeText(context, "Comment deleted!", Toast.LENGTH_SHORT).show()
                                commentToDelete = null
                            }.onFailure { error ->
                                Toast.makeText(context, "Error deleting comment: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { commentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEditDialog && selectedIssue != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedIssue = selectedIssue!!.copy(
                        category = editCategory,
                        description = editDescription
                    )
                    issueViewModel.updateIssue(updatedIssue) { result ->
                        result.onSuccess {
                            Toast.makeText(context, "Report updated", Toast.LENGTH_SHORT).show()
                            showEditDialog = false
                        }.onFailure {
                            Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Incident Details", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        val issue = selectedIssue
        if (issue == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Setup OSMDroid userAgent
            Configuration.getInstance().userAgentValue = context.packageName

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // We use LazyColumn to display details + comments seamlessly
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Header Image Item
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                        ) {
                            if (!issue.imageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = issue.imageUrl,
                                    contentDescription = "Incident image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }

                            // Overlay Category & Status Badges
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                                    .align(Alignment.TopStart),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        issue.category.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = getStatusColor(issue.status)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        issue.status.uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Metadata Section
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                issue.title,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                    Text(
                                        "By ${issue.reporterName}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                Text(
                                    formatDate(issue.timestamp),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    // Description text
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "INCIDENT DESCRIPTION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    issue.description,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // Coordinates & Location details
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("LATITUDE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text(String.format("%.5f", issue.latitude), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("LONGITUDE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text(String.format("%.5f", issue.longitude), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            if (issue.address.isNotBlank()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("ADDRESS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                        Text(issue.address, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                            
                            // Edit and Delete buttons for reporter if Pending
                            if (issue.reporterId == currentUserId && issue.status.equals("Pending", ignoreCase = true)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            editCategory = issue.category
                                            editDescription = issue.description
                                            showEditDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit")
                                    }
                                    
                                    Button(
                                        onClick = { 
                                            issueViewModel.deleteIssue(issue.id) { result ->
                                                result.onSuccess {
                                                    Toast.makeText(context, "Report deleted", Toast.LENGTH_SHORT).show()
                                                    onNavigateBack()
                                                }.onFailure {
                                                    Toast.makeText(context, "Failed to delete", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete")
                                    }
                                }
                            }
                        }
                    }

                    // Embedded Interactive Map Preview
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "MAP LOCATION PREVIEW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        MapView(ctx).apply {
                                            setMultiTouchControls(true)
                                            controller.setZoom(15.5)
                                            val geoPoint = GeoPoint(issue.latitude, issue.longitude)
                                            controller.setCenter(geoPoint)

                                            val marker = Marker(this).apply {
                                                position = geoPoint
                                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                                title = issue.title
                                            }
                                            overlays.add(marker)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Community Actions Section Header
                    item {
                        Text(
                            "COMMUNITY ACTIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Interaction Actions Row (Upvote, Comment-count, Share)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val userHasUpvoted = userHasVotedState || issue.upvotedBy.contains(currentUserId)
                            Button(
                                onClick = {
                                    if (userHasUpvoted) {
                                        Toast.makeText(context, "You have already voted.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        issueViewModel.upvoteIssue(issue.id, currentUserId) { result ->
                                            result.onSuccess {
                                                userHasVotedState = true
                                                Toast.makeText(context, "Vote cast successfully!", Toast.LENGTH_SHORT).show()
                                            }.onFailure { error ->
                                                Toast.makeText(context, "Error: ${error.message ?: "Failed to vote"}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                enabled = !userHasUpvoted,
                                modifier = Modifier.weight(1.2f).testTag("vote_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (userHasUpvoted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                                    contentColor = if (userHasUpvoted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Icon(
                                    if (userHasUpvoted) Icons.Default.Check else Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (userHasUpvoted) "✓ You voted for this report." else "Vote (${issue.votes})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val shareText = "UrbanPulse Report Alert!\n" +
                                            "Category: ${issue.category}\n" +
                                            "Status: ${issue.status}\n" +
                                            "Coordinates: (${issue.latitude}, ${issue.longitude})\n" +
                                            "Description: ${issue.description}\n" +
                                            "Reported via UrbanPulse app."
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Report via"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Comments Section Heading
                    item {
                        Text(
                            "CITIZEN FORUM COMMENTS (${issue.comments.size})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (issue.comments.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    "No comments left on this report yet. Start the conversation below!",
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    // Comments List items
                    items(issue.comments) { comment ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            comment.username,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (comment.userId == currentUserId) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "You",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            formatDate(comment.timestamp),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        if (comment.userId == currentUserId) {
                                            IconButton(
                                                onClick = {
                                                    editingComment = comment
                                                    editingCommentText = comment.commentText
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit comment",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    commentToDelete = comment
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete comment",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    comment.commentText,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Comment input box at the bottom
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { 
                                if (it.length <= 300) {
                                    commentText = it 
                                }
                            },
                            placeholder = { Text("Write a comment...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            maxLines = 3,
                            suffix = {
                                if (commentText.isNotEmpty()) {
                                    Text(
                                        "${commentText.length}/300",
                                        fontSize = 10.sp,
                                        color = if (commentText.length >= 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    if (commentText.length > 300) {
                                        Toast.makeText(context, "Comment exceeds the maximum length of 300 characters.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isSubmittingComment = true
                                        val newComment = Comment(
                                            userId = currentUserId,
                                            username = currentUserName,
                                            commentText = commentText.trim()
                                        )
                                        issueViewModel.addComment(issue.id, newComment) { result ->
                                            isSubmittingComment = false
                                            result.onSuccess {
                                                commentText = ""
                                                Toast.makeText(context, "Comment added!", Toast.LENGTH_SHORT).show()
                                            }.onFailure { error ->
                                                Toast.makeText(context, "Error adding comment: ${error.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isSubmittingComment && commentText.isNotBlank() && commentText.length <= 300,
                            modifier = Modifier
                                .background(
                                    if (!isSubmittingComment && commentText.isNotBlank() && commentText.length <= 300) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                )
                                .size(44.dp)
                        ) {
                            if (isSubmittingComment) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (commentText.isNotBlank() && commentText.length <= 300) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility formatting date
private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    return format.format(date)
}

// Get appropriate color matching status
private fun getStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "fixed" -> Color(0xFF2E7D32) // Dark green
        "reviewing" -> Color(0xFFFFA500) // Orange
        "pending" -> Color(0xFFC62828) // Red-dark
        else -> Color.Gray
    }
}
