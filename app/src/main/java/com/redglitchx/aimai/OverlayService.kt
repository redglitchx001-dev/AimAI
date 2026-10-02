package com.redglitchx.aimai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.view.View

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                var isExpanded by remember { mutableStateOf(false) }
                
                if (isExpanded) {
                    ExpandedOverlay(
                        context = this@OverlayService,
                        onClose = { isExpanded = false },
                        onCloseService = { stopSelf() }
                    )
                } else {
                    FloatingBubble(
                        onClick = { isExpanded = true },
                        params = params,
                        windowManager = windowManager,
                        view = this
                    )
                }
            }
        }
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager.addView(composeView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun FloatingBubble(
    onClick: () -> Unit,
    params: WindowManager.LayoutParams,
    windowManager: WindowManager,
    view: View
) {
    var offsetX by remember { mutableStateOf(params.x.toFloat()) }
    var offsetY by remember { mutableStateOf(params.y.toFloat()) }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .padding(4.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                        params.x = offsetX.toInt()
                        params.y = offsetY.toInt()
                        windowManager.updateViewLayout(view, params)
                    },
                    onDragEnd = {
                        // Optional snap to edge could be added here
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("AIM\nAI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun ExpandedOverlay(context: Context, onClose: () -> Unit, onCloseService: () -> Unit) {
    val prefs = context.getSharedPreferences("AimAIPrefs", Context.MODE_PRIVATE)

    var aimOn by remember { mutableStateOf(false) }
    var scopeOnly by remember { mutableStateOf(true) }
    var normalAim by remember { mutableStateOf(true) }
    var aimSpeed by remember { mutableStateOf(50f) }
    var fov by remember { mutableStateOf(30f) }
    var bypass by remember { mutableStateOf(true) }
    var antiCheat by remember { mutableStateOf(true) }
    
    val selectedApp = prefs.getString("selected_app_name", "Select Target App") ?: "Select Target App"
    val selectedModel = prefs.getString("selected_model", "Auto-detect Model (HTTPS)") ?: "Auto-detect Model (HTTPS)"

    Card(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.DarkGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SYSTEM.AIM_AI [v1.0.0]", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("X", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
            
            // App and Model Selection
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Text("App: $selectedApp", color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Text("Model: $selectedModel", color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Toggles
            TechSwitch("Master AIM Switch", aimOn) { aimOn = it }
            TechSwitch("Normal Aim Mode", normalAim) { normalAim = it }
            TechSwitch("In-Scope Only", scopeOnly) { scopeOnly = it }
            TechSwitch("Bypass Detection", bypass) { bypass = it }
            TechSwitch("Anti-Cheat Blocker", antiCheat) { antiCheat = it }

            Spacer(modifier = Modifier.height(12.dp))
            
            // Sliders
            TechSlider("Aim Speed / Smoothing", aimSpeed) { aimSpeed = it }
            TechSlider("FOV Radius", fov) { fov = it }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCloseService,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("DELETE BUBBLE & TERMINATE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun TechSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text("> $label", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Color.White, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color.DarkGray)
        )
    }
}

@Composable
fun TechSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("> $label: ${value.toInt()}%", color = Color.White, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.DarkGray
            )
        )
    }
}
