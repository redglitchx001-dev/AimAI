package com.redglitchx.aimai

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.runtime.LaunchedEffect
import org.json.JSONArray

data class AppInfo(val name: String, val packageName: String)

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, "[ERR] PERMISSION_DENIED", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    MainScreen(
                        context = this,
                        onStartOverlay = { checkAndStartOverlay() }
                    )
                }
            }
        }
    }

    private fun checkAndStartOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
        Toast.makeText(this, "[SYS] OVERLAY_INJECTED", Toast.LENGTH_SHORT).show()
    }
}

fun getInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val flags = PackageManager.MATCH_ALL
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
    } else {
        pm.queryIntentActivities(intent, flags)
    }
    return resolveInfos.map {
        AppInfo(
            name = it.loadLabel(pm).toString(),
            packageName = it.activityInfo.packageName
        )
    }.distinctBy { it.packageName }.sortedBy { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context, onStartOverlay: () -> Unit) {
    val prefs = context.getSharedPreferences("AimAIPrefs", Context.MODE_PRIVATE)

    var aiEnabled by remember { mutableStateOf(true) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "https://api.models.local/v1/detect") ?: "https://api.models.local/v1/detect") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var customModelId by remember { mutableStateOf(prefs.getString("custom_model_id", "") ?: "") }
    var selectedModel by remember { mutableStateOf(prefs.getString("selected_model", "[SELECT MODEL]") ?: "[SELECT MODEL]") }
    var showModelDialog by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf(listOf("[YOLOv8-Fast]", "[YOLOv10-Silent]", "[Auto-Detect HTTPS]")) }

    var showAppDialog by remember { mutableStateOf(false) }
    var selectedAppPackage by remember { mutableStateOf(prefs.getString("selected_app_pkg", "") ?: "") }
    var selectedAppName by remember { mutableStateOf(prefs.getString("selected_app_name", "") ?: "") }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    var isFetchingModels by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }

    fun savePrefs() {
        prefs.edit()
            .putString("selected_app_pkg", selectedAppPackage)
            .putString("selected_app_name", selectedAppName)
            .putString("selected_model", selectedModel)
            .putString("endpoint", endpoint)
            .putString("api_key", apiKey)
            .putString("custom_model_id", customModelId)
            .apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SYS.AIM_AI // CONFIG",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        Button(
            onClick = onStartOverlay,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.fillMaxWidth().height(55.dp)
        ) {
            Text("> INJECT OVERLAY <", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // TARGET APP SELECTION
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("[ TARGET PROCESS ]", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { showAppDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedAppName.isNotEmpty()) selectedAppName else "SELECT TARGET APP", color = Color.White, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // AI CONFIGURATION
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("[ AI_ENGINE ]", color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("> ENABLE ENGINE", color = Color.LightGray, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { aiEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White, uncheckedThumbColor = Color.Gray)
                    )
                }

                if (aiEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; savePrefs() },
                        label = { Text("HTTPS ENDPOINT", color = Color.Gray, fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it; savePrefs() },
                        label = { Text("API KEY (HTTPS Auth)", color = Color.Gray, fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customModelId,
                        onValueChange = { customModelId = it; savePrefs() },
                        label = { Text("OPTIONAL MODEL ID", color = Color.Gray, fontFamily = FontFamily.Monospace) },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.DarkGray,
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { 
                            // Simulate fetch from HTTPS
                            isFetchingModels = true
                            showModelDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedModel, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(30.dp))
        Text("STATUS: UNDETECTED", color = Color.Green, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Text("BUILD: PROGUARD_ENABLED", color = Color.Green, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.weight(1f))
        Text("© RED_GLITCH_X // ROOTED", color = Color.DarkGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 16.dp))
    }

    if (showAppDialog) {
        AlertDialog(
            onDismissRequest = { showAppDialog = false },
            containerColor = Color.Black,
            titleContentColor = Color.White,
            title = { Text("[ SELECT PROCESS ]", fontFamily = FontFamily.Monospace) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                    items(installedApps) { app ->
                        Text(
                            text = "> ${app.name}",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedAppName = app.name
                                    selectedAppPackage = app.packageName
                                    savePrefs()
                                    showAppDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppDialog = false }) { Text("CANCEL", color = Color.White, fontFamily = FontFamily.Monospace) }
            }
        )
    }

    if (showModelDialog) {
        LaunchedEffect(isFetchingModels) {
            if (isFetchingModels) {
                // Simulating network delay to fetch from HTTPS
                kotlinx.coroutines.delay(1000)
                availableModels = listOf("[YOLOv8-Fast]", "[YOLOv10-Silent]", "[Cloud-AI-Bypass]", "[Auto-Detect HTTPS]")
                isFetchingModels = false
            }
        }

        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            containerColor = Color.Black,
            titleContentColor = Color.White,
            title = { Text(if (isFetchingModels) "FETCHING FROM HTTPS..." else "[ FETCHED MODELS ]", fontFamily = FontFamily.Monospace) },
            text = {
                if (!isFetchingModels) {
                    Column {
                        availableModels.forEach { model ->
                            Text(
                                text = "> $model",
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedModel = model
                                        savePrefs()
                                        showModelDialog = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                } else {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(16.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) { Text("CLOSE", color = Color.White, fontFamily = FontFamily.Monospace) }
            }
        )
    }
}
