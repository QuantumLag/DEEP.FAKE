package com.qlcom.hack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qlcom.hack.data.AppDatabase
import com.qlcom.hack.ui.screens.CameraScreen
import com.qlcom.hack.ui.screens.GalleryScreen
import com.qlcom.hack.ui.theme.CyberCyan
import com.qlcom.hack.ui.theme.DarkBackground
import com.qlcom.hack.ui.theme.DarkSurface
import com.qlcom.hack.ui.theme.DarkSurfaceVariant
import com.qlcom.hack.ui.theme.NeonPurple
import com.qlcom.hack.ui.theme.QlcomHackTheme
import com.qlcom.hack.ui.theme.TextPrimary
import com.qlcom.hack.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase
    private var hasCameraPermission by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        if (!hasCameraPermission) {
            Toast.makeText(this, "Camera access is critical for deepfake analysis.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        database = AppDatabase.getDatabase(this)
        
        // Initial permission check
        checkAndRequestPermissions()

        setContent {
            QlcomHackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (hasCameraPermission) {
                        MainScreenContainer(database)
                    } else {
                        PermissionDeniedOverlay(
                            onRequestPermissions = { checkAndRequestPermissions() }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val allGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        
        if (allGranted) {
            hasCameraPermission = true
        } else {
            val permissionRequestList = mutableListOf(Manifest.permission.CAMERA)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissionRequestList.add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                permissionRequestList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            requestPermissionLauncher.launch(permissionRequestList.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContainer(database: AppDatabase) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(listOf(CyberCyan.copy(alpha = 0.15f), Color.Transparent)),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Camera Scanner") },
                    label = { Text("SECURE SCAN", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Media Ledger") },
                    label = { Text("LEDGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyberCyan,
                        selectedTextColor = CyberCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = DarkSurfaceVariant
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedTab) {
                0 -> CameraScreen(database = database)
                1 -> GalleryScreen(database = database)
            }
        }
    }
}

@Composable
fun PermissionDeniedOverlay(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CAMERA ACCESS REQUIRED",
            color = MaterialTheme.colorScheme.error,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "To run real-time deepfake checks and secure cryptographic watermarks, this app must access the system camera device.",
            color = TextSecondary,
            fontSize = 13.sp,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermissions,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(48.dp).width(200.dp)
        ) {
            Text("GRANT PERMISSION", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
