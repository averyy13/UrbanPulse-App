package com.example.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.IssueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToMyReports: () -> Unit,
    viewModel: AuthViewModel,
    issueViewModel: IssueViewModel
) {
    val authState by viewModel.authState.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val issues by issueViewModel.issues.collectAsState()

    // Redirect to login if user logs out successfully
    LaunchedEffect(authState) {
        if (authState !is AuthState.Success) {
            onNavigateToLogin()
        }
    }

    val email = if (authState is AuthState.Success) (authState as AuthState.Success).email else "citizen@urbanpulse.org"
    val displayName = if (authState is AuthState.Success) {
        (authState as AuthState.Success).displayName ?: "Active Citizen"
    } else {
        "Active Citizen"
    }

    // Dynamic metrics calculation
    val myReportsCount = remember(issues, email) {
        issues.count { it.reporterId.equals(email, ignoreCase = true) }
    }
    val myUpvotesReceived = remember(issues, email) {
        issues.filter { it.reporterId.equals(email, ignoreCase = true) }.sumOf { it.votes }
    }
    val userRank = remember(myReportsCount) {
        when {
            myReportsCount >= 5 -> "Super Warden"
            myReportsCount >= 3 -> "Safety Leader"
            myReportsCount >= 1 -> "Active Guardian"
            else -> "Apprentice Watch"
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Medium, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Avatar Circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (displayName.isNotEmpty()) displayName.take(2).uppercase() else "UP",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // User Identification Details
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = email,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Member since: Jan 2024",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isDemoMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Demo Sandbox Account",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User stats banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My Reports", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("$myReportsCount", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.secondaryContainer))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Upvotes", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        Text("$myUpvotesReceived", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.secondaryContainer))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Rank", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        Text(userRank, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Interactive Profile options list
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileOptionItem(icon = Icons.Default.List, title = "My Reports", onClick = onNavigateToMyReports)
                    HorizontalDivider(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ProfileOptionItem(icon = Icons.Default.Settings, title = "Settings")
                    HorizontalDivider(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ProfileOptionItem(icon = Icons.Default.Info, title = "Help & Support")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Prominent Logout Button
            Button(
                onClick = { viewModel.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logout_button")
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Log Out",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sign Out",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ProfileOptionItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
    }
}
