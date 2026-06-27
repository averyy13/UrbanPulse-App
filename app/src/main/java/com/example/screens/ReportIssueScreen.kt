package com.example.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.model.Issue
import com.example.viewmodel.AuthState
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.IssueViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "captured_image_${System.currentTimeMillis()}.jpg")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        android.util.Log.e("ReportIssueScreen", "Error saving bitmap to cache: ${e.message}")
        null
    }
}

private fun getAddressFromLatLng(context: Context, lat: Double, lng: Double): String {
    return try {
        val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            addresses[0].getAddressLine(0) ?: "$lat, $lng"
        } else {
            "Address near $lat, $lng"
        }
    } catch (e: Exception) {
        "Pinpointed Location ($lat, $lng)"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ReportIssueScreen(
    onNavigateBack: () -> Unit,
    issueViewModel: IssueViewModel,
    authViewModel: AuthViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Form inputs
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // Photo management
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedPresetUrl by remember { mutableStateOf<String?>(null) }

    // Coordinates and Address state
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var address by remember { mutableStateOf("") }

    // Dropdown state
    var expandedDropdown by remember { mutableStateOf(false) }

    // Loader/Uploading
    val isUploading by issueViewModel.isUploading.collectAsState()

    // Full-screen map selection mode
    var isMapSelectionOpen by remember { mutableStateOf(false) }

    // Dropdown Categories
    val categories = listOf("Pothole", "Light Out", "Waste", "Water Leak", "Road Sign", "Other")

    // Auth details
    val authState by authViewModel.authState.collectAsState()
    val userId = if (authState is AuthState.Success) (authState as AuthState.Success).email else "anonymous"
    val userName = if (authState is AuthState.Success) {
        (authState as AuthState.Success).displayName ?: "Active Citizen"
    } else {
        "Active Citizen"
    }

    // Launchers for Camera & Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            capturedBitmap = null
            selectedPresetUrl = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val savedUri = saveBitmapToCache(context, bitmap)
            if (savedUri != null) {
                selectedImageUri = savedUri
                capturedBitmap = null
                selectedPresetUrl = null
            } else {
                capturedBitmap = bitmap
                selectedImageUri = null
                selectedPresetUrl = null
            }
        }
    }

    // Camera permission state
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    // Location permission state
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Fetch location function
    @SuppressLint("MissingPermission")
    fun fetchCurrentGPS() {
        if (locationPermissionsState.allPermissionsGranted) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnCompleteListener { task: Task<android.location.Location> ->
                val loc = task.result
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    address = getAddressFromLatLng(context, loc.latitude, loc.longitude)
                    Toast.makeText(context, "Location updated: ${loc.latitude}, ${loc.longitude}", Toast.LENGTH_SHORT).show()
                } else {
                    // Fallback to SF downtown inside mock simulation safely
                    latitude = 37.7749
                    longitude = -122.4194
                    address = getAddressFromLatLng(context, 37.7749, -122.4194)
                    Toast.makeText(context, "Using default location coordinates.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    if (isMapSelectionOpen) {
        // Fullscreen OSMDroid Selector Screen
        MapLocationPickerScreen(
            initialLat = latitude ?: 37.7749,
            initialLng = longitude ?: -122.4194,
            onLocationConfirmed = { lat, lng, resolvedAddress ->
                latitude = lat
                longitude = lng
                address = resolvedAddress
                isMapSelectionOpen = false
            },
            onClose = { isMapSelectionOpen = false }
        )
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Submit Infrastructure Report", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(scrollState)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Category Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "INCIDENT CATEGORY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        Box {
                            OutlinedCard(
                                onClick = { expandedDropdown = true },
                                modifier = Modifier.fillMaxWidth().testTag("category_selector_card"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (category.isEmpty()) "Select a Category" else category,
                                        color = if (category.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("selected_category_text")
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }

                            DropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false },
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(Color.White)
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = cat,
                                                color = Color(0xFF1A1C19),
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            ) 
                                        },
                                        onClick = {
                                            category = cat
                                            expandedDropdown = false
                                        },
                                        modifier = Modifier.testTag("category_item_$cat")
                                    )
                                }
                            }
                        }
                    }

                    // Description Input
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "DESCRIPTION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Describe the problem so technicians can understand the details...", fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("description_input"),
                            minLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Photo Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "INCIDENT PHOTO",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )

                        // Image Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                                .testTag("image_preview_box"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (capturedBitmap != null) {
                                Image(
                                    bitmap = capturedBitmap!!.asImageBitmap(),
                                    contentDescription = "Captured Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (selectedPresetUrl != null) {
                                AsyncImage(
                                    model = selectedPresetUrl,
                                    contentDescription = "Preset Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No photo attached yet",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }

                        // Photo Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (cameraPermissionState.status.isGranted) {
                                        cameraLauncher.launch()
                                    } else {
                                        cameraPermissionState.launchPermissionRequest()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("take_photo_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Photo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f).testTag("gallery_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Gallery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Sandbox Preset Quick-Picker
                        Text(
                            "Or choose a preset preview:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val presetPhotos = listOf(
                                "Pothole" to "https://images.unsplash.com/photo-1515162305285-0293e4767cc2?auto=format&fit=crop&q=80&w=400",
                                "Broken Light" to "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&q=80&w=400",
                                "Waste" to "https://images.unsplash.com/photo-1611284446314-60a58ac0deb9?auto=format&fit=crop&q=80&w=400",
                                "Water Leak" to "https://images.unsplash.com/photo-1504328345606-18bbc8c9d7d1?auto=format&fit=crop&q=80&w=400"
                            )
                            presetPhotos.forEach { (label, url) ->
                                val isSelected = selectedPresetUrl == url
                                val borderStroke = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer)
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    border = borderStroke,
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .clickable {
                                            selectedPresetUrl = url
                                            selectedImageUri = null
                                            capturedBitmap = null
                                            if (label == "Broken Light") {
                                                category = "Light Out"
                                            } else {
                                                category = label
                                            }
                                        }
                                        .testTag("preset_card_$label"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                                        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }

                    // Location Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "INCIDENT LOCATION",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.sp
                        )

                        // Coordinates Banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("location_info_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        if (latitude != null && longitude != null) {
                                            Text("Latitude: ${String.format("%.5f", latitude)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Longitude: ${String.format("%.5f", longitude)}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("No location coordinates selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                                            Text("Please choose or fetch below", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
                                        }
                                    }
                                }

                                if (address.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.secondaryContainer)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Column {
                                            Text("Resolved Address:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                                            Text(address, fontSize = 13.sp, fontWeight = FontWeight.Normal, modifier = Modifier.testTag("resolved_address_text"))
                                        }
                                    }
                                }
                            }
                        }

                        // Location Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { fetchCurrentGPS() },
                                modifier = Modifier.weight(1f).testTag("use_gps_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Use GPS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = { isMapSelectionOpen = true },
                                modifier = Modifier.weight(1f).testTag("select_map_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Map", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Final Submit Button
                    val isFormValid = category.isNotEmpty() && 
                            description.isNotBlank() && 
                            (selectedImageUri != null || capturedBitmap != null || selectedPresetUrl != null) && 
                            latitude != null && longitude != null

                    Button(
                        onClick = {
                            if (!isFormValid) {
                                when {
                                    category.isEmpty() -> {
                                        Toast.makeText(context, "Please select an incident category", Toast.LENGTH_SHORT).show()
                                    }
                                    description.isBlank() -> {
                                        Toast.makeText(context, "Please enter a description", Toast.LENGTH_SHORT).show()
                                    }
                                    (selectedImageUri == null && capturedBitmap == null && selectedPresetUrl == null) -> {
                                        Toast.makeText(context, "Please attach a photo or select a preset", Toast.LENGTH_SHORT).show()
                                    }
                                    (latitude == null || longitude == null) -> {
                                        Toast.makeText(context, "Please select a location on the map or use GPS", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                val photoUrl = selectedPresetUrl ?: selectedImageUri?.toString() ?: ""
                                val newIssue = Issue(
                                    title = "$category Issue",
                                    description = description,
                                    category = category,
                                    latitude = latitude ?: 0.0,
                                    longitude = longitude ?: 0.0,
                                    address = address.ifEmpty { "San Francisco, CA" },
                                    imageUrl = photoUrl,
                                    status = "Pending",
                                    reporterId = userId,
                                    reporterName = userName,
                                    priorityScore = 0,
                                    timestamp = System.currentTimeMillis()
                                )
                                issueViewModel.addIssue(newIssue) { result ->
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Report Submitted Successfully!", Toast.LENGTH_LONG).show()
                                        onNavigateBack()
                                    } else {
                                        Toast.makeText(context, "Error submitting report: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth().testTag("submit_report_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFormValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        } else {
                            Text("Submit Incident Report", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapLocationPickerScreen(
    initialLat: Double,
    initialLng: Double,
    onLocationConfirmed: (Double, Double, String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var markerPos by remember { mutableStateOf(GeoPoint(initialLat, initialLng)) }
    var resolvedAddress by remember { mutableStateOf("") }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Ensure OSMDroid has correct UserAgent configured
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        resolvedAddress = getAddressFromLatLng(context, initialLat, initialLng)
    }

    // Location permission state
    val locationPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Center to user function
    @SuppressLint("MissingPermission")
    fun centerToUser() {
        if (locationPermissionsState.allPermissionsGranted) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.lastLocation.addOnCompleteListener { task: Task<android.location.Location> ->
                val loc = task.result
                if (loc != null) {
                    val userPoint = GeoPoint(loc.latitude, loc.longitude)
                    markerPos = userPoint
                    resolvedAddress = getAddressFromLatLng(context, loc.latitude, loc.longitude)
                    mapViewInstance?.controller?.animateTo(userPoint)
                } else {
                    Toast.makeText(context, "Could not fetch GPS location. Ensure location is enabled on device.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            locationPermissionsState.launchMultiplePermissionRequest()
        }
    }

    // Auto-center on start if permission is already granted
    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        if (locationPermissionsState.allPermissionsGranted && initialLat == 37.7749 && initialLng == -122.4194) {
            centerToUser()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Select Location on Map", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_map_picker")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Android View rendering the OSMDroid map
            AndroidView(
                modifier = Modifier.fillMaxSize().testTag("osmdroid_map"),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                        controller.setCenter(markerPos)
                        mapViewInstance = this

                        // Add main marker
                        val marker = Marker(this).apply {
                            position = markerPos
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Drag me or Tap the map!"
                            isDraggable = true
                            
                            // Track marker dragging
                            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                override fun onMarkerDrag(m: Marker?) {}
                                override fun onMarkerDragEnd(m: Marker?) {
                                    m?.position?.let {
                                        markerPos = it
                                        resolvedAddress = getAddressFromLatLng(context, it.latitude, it.longitude)
                                    }
                                }
                                override fun onMarkerDragStart(m: Marker?) {}
                            })
                        }
                        overlays.add(marker)

                        // Track map single tap to move the marker
                        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                if (p != null) {
                                    markerPos = p
                                    marker.position = p
                                    resolvedAddress = getAddressFromLatLng(context, p.latitude, p.longitude)
                                    invalidate()
                                    return true
                                }
                                return false
                            }
                            override fun longPressHelper(p: GeoPoint?): Boolean = false
                        })
                        overlays.add(eventsOverlay)
                    }
                },
                update = { mapView ->
                    val marker = mapView.overlays.filterIsInstance<Marker>().firstOrNull()
                    if (marker != null && marker.position != markerPos) {
                        marker.position = markerPos
                        val currentCenter = mapView.mapCenter
                        if (Math.abs(currentCenter.latitude - markerPos.latitude) > 0.001 || 
                            Math.abs(currentCenter.longitude - markerPos.longitude) > 0.001) {
                            mapView.controller.animateTo(markerPos)
                        }
                        mapView.invalidate()
                    }
                }
            )

            // Floating indicator on top
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Selected Address:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = resolvedAddress.ifEmpty { "Resolving address..." },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("map_resolved_address_text")
                        )
                    }
                }

                Button(
                    onClick = { centerToUser() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("use_current_location_button")
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Current Location", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Confirm Selection Button
            Button(
                onClick = { onLocationConfirmed(markerPos.latitude, markerPos.longitude, resolvedAddress) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(52.dp)
                    .testTag("confirm_location_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm Selected Location", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
