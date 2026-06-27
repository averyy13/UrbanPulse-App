package com.example.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Issue
import com.example.ui.theme.*

@Composable
fun IssueCard(
    issue: Issue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (issue.status.lowercase()) {
        "pending" -> StatusPendingBg
        "reviewing" -> StatusReviewingBg
        "fixed" -> StatusFixedBg
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    
    val statusTextColor = when (issue.status.lowercase()) {
        "pending" -> StatusPendingText
        "reviewing" -> StatusReviewingText
        "fixed" -> StatusFixedText
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    val distanceStr = calculateDistance(issue.latitude, issue.longitude)
    val relativeTime = getRelativeTime(issue.timestamp)
    val priorityLabel = getPriorityLabel(issue.priorityScore)
    val priorityColor = getPriorityColor(issue.priorityScore)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("issue_card_${issue.id}")
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Column: Photo Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            ) {
                if (!issue.imageUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = issue.imageUrl,
                        contentDescription = "Incident thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = issue.category.take(1).uppercase(),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Right Column: Details & Badges
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Category & Time Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = issue.category.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = relativeTime,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Title
                Text(
                    text = issue.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                Text(
                    text = issue.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Metadata details row (Distance, Votes, Priority & Status Badges)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Distance & Votes Counter
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = distanceStr,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Upvotes",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${issue.votes}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Priority Badge & Status Badge Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Priority Badge
                        Card(
                            colors = CardDefaults.cardColors(containerColor = priorityColor.copy(alpha = 0.15f)),
                            shape = CircleShape,
                            border = BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = priorityLabel.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Status Badge
                        Box(
                            modifier = Modifier
                                .background(statusColor, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = issue.status.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusTextColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// Haversine calculator to find relative distance from a center point (SF Center)
private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double = 37.7749, lon2: Double = -122.4194): String {
    val r = 6371 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    val d = r * c
    return if (d < 1.0) {
        "${(d * 1000).toInt()}m away"
    } else {
        "${String.format("%.1f", d)}km away"
    }
}

private fun getRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}

private fun getPriorityLabel(score: Int): String {
    return when {
        score >= 3 -> "Critical"
        score == 2 -> "High"
        score == 1 -> "Medium"
        else -> "Low"
    }
}

private fun getPriorityColor(score: Int): Color {
    return when {
        score >= 3 -> Color(0xFFC62828)
        score == 2 -> Color(0xFFE65100)
        score == 1 -> Color(0xFFFBC02D)
        else -> Color(0xFF2E7D32)
    }
}
