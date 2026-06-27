package com.example.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.Issue
import com.example.viewmodel.IssueViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onNavigateToDetails: (String) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToReport: () -> Unit,
    viewModel: IssueViewModel
) {
    val context = LocalContext.current
    val issues by viewModel.issues.collectAsState()

    // Filters State
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedPriorityFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember { mutableStateOf("All") }

    val categoriesList = listOf("All", "Pothole", "Light Out", "Waste", "Water Leak", "Road Sign", "Other")
    val prioritiesList = listOf("All", "Critical", "High", "Medium", "Low")
    val statusesList = listOf("All", "Pending", "Reviewing", "Fixed")

    // Filter logic
    val filteredIssues = remember(issues, selectedCategoryFilter, selectedPriorityFilter, selectedStatusFilter) {
        issues.filter { issue ->
            val matchesCategory = selectedCategoryFilter == "All" || issue.category == selectedCategoryFilter
            
            val matchesPriority = selectedPriorityFilter == "All" || when (selectedPriorityFilter) {
                "Critical" -> issue.priorityScore >= 3
                "High" -> issue.priorityScore == 2
                "Medium" -> issue.priorityScore == 1
                "Low" -> issue.priorityScore <= 0
                else -> true
            }

            val matchesStatus = selectedStatusFilter == "All" || issue.status.equals(selectedStatusFilter, ignoreCase = true)

            matchesCategory && matchesPriority && matchesStatus
        }
    }

    // Set OSMDroid UserAgent configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Neighborhood Heatmap", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = onNavigateToReport) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = "Report Location", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToHome,
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                )
                NavigationBarItem(
                    selected = true,
                    onClick = { },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                    label = { Text("Map") },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToProfile,
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Scroll Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 8.dp)
            ) {
                // Category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categoriesList.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat, fontSize = 12.sp) }
                        )
                    }
                }

                // Priority & Status Chips Group Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    prioritiesList.forEach { pri ->
                        FilterChip(
                            selected = selectedPriorityFilter == pri,
                            onClick = { selectedPriorityFilter = pri },
                            label = { Text("Priority: $pri", fontSize = 11.sp) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(getComposePriorityColor(pri), CircleShape)
                                )
                            }
                        )
                    }

                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.secondaryContainer))

                    statusesList.forEach { stat ->
                        FilterChip(
                            selected = selectedStatusFilter == stat,
                            onClick = { selectedStatusFilter = stat },
                            label = { Text("Status: $stat", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Map View container
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            controller.setZoom(14.5)
                            // Focus map on SF Center
                            controller.setCenter(GeoPoint(37.7749, -122.4194))
                        }
                    },
                    update = { mapView ->
                        // Clear old markers, preserve map configuration
                        mapView.overlays.clear()

                        // Add markers for filtered issues
                        filteredIssues.forEach { issue ->
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(issue.latitude, issue.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = issue.title
                                snippet = "${issue.category} • ${issue.status.uppercase()}"
                                
                                // Generate colored dot pin programmatically based on priorityScore
                                val androidColorInt = when {
                                    issue.priorityScore >= 3 -> AndroidColor.RED // Critical
                                    issue.priorityScore == 2 -> AndroidColor.rgb(255, 140, 0) // Orange (High)
                                    issue.priorityScore == 1 -> AndroidColor.rgb(240, 200, 0) // Yellow (Medium)
                                    else -> AndroidColor.rgb(34, 139, 34) // Green (Low)
                                }
                                icon = createCustomMarkerIcon(context, androidColorInt)

                                setOnMarkerClickListener { m, _ ->
                                    m.showInfoWindow()
                                    // Clicking marker or info window navigates directly to Report Details
                                    onNavigateToDetails(issue.id)
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                        }
                        mapView.invalidate()
                    }
                )

                // Colored Legend Map Overlay
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("INCIDENT HEATMAP LEGEND", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color.Red, CircleShape))
                            Text("Critical Priority", fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFA500), CircleShape))
                            Text("High Priority", fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFD700), CircleShape))
                            Text("Medium Priority", fontSize = 10.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF2E7D32), CircleShape))
                            Text("Low Priority", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// Generate stylized pin marker drawable dynamically without XML files
private fun createCustomMarkerIcon(context: Context, pinColorInt: Int): Drawable {
    val diameter = 54
    val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = pinColorInt
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Draw base outer circle
    canvas.drawCircle((diameter / 2).toFloat(), (diameter / 2).toFloat(), (diameter / 2 - 2).toFloat(), paint)

    // Inner white border circle
    paint.color = AndroidColor.WHITE
    canvas.drawCircle((diameter / 2).toFloat(), (diameter / 2).toFloat(), (diameter / 4).toFloat(), paint)

    // Inner center pin dot circle
    paint.color = pinColorInt
    canvas.drawCircle((diameter / 2).toFloat(), (diameter / 2).toFloat(), (diameter / 7).toFloat(), paint)

    return BitmapDrawable(context.resources, bitmap)
}

// Helper to get compose color from priority label
private fun getComposePriorityColor(priorityLabel: String): Color {
    return when (priorityLabel) {
        "Critical" -> Color.Red
        "High" -> Color(0xFFFFA500)
        "Medium" -> Color(0xFFFFD700)
        "Low" -> Color(0xFF2E7D32)
        else -> Color.Gray
    }
}
