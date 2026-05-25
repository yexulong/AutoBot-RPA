package com.autobot.rpa.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.autobot.rpa.MainActivity
import com.autobot.rpa.R
import com.autobot.rpa.service.AutoBotAccessibilityService
import com.autobot.rpa.service.ScreenshotManager
import com.autobot.rpa.service.TextRecognitionService
import com.autobot.rpa.service.TextMatchResult
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "settings_main"
    ) {
        composable("settings_main") {
            SettingsMainScreen(navController = navController)
        }
        composable("screenshots_list") {
            ScreenshotsListScreen(navController = navController)
        }
        composable("test_text_recognition") {
            TestTextRecognitionScreen(navController = navController)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isAccessibilityEnabled by remember { mutableStateOf(AutoBotAccessibilityService.isAccessibilityServiceEnabled()) }
    var isOverlayGranted by remember { mutableStateOf(checkOverlayPermission(context)) }
    
    // 使用 collectAsState 直接监听权限状态
    val screenshotManager = remember { ScreenshotManager.getInstance(context) }
    val permissionState by screenshotManager.permissionState.collectAsState(initial = ScreenshotManager.PermissionState.NotRequested)
    val isScreenshotGranted = permissionState == ScreenshotManager.PermissionState.Granted
    
    // 监听生命周期，当页面恢复时重新检查权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("SettingsScreen", "onResume, permissionState: $permissionState")
                isAccessibilityEnabled = AutoBotAccessibilityService.isAccessibilityServiceEnabled()
                isOverlayGranted = checkOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // 监听 AutoBotAccessibilityService 的状态流
    LaunchedEffect(Unit) {
        AutoBotAccessibilityService.isServiceRunning.collect { running ->
            isAccessibilityEnabled = running
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsSection(title = "Permissions") {
                SettingsItem(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service",
                    description = if (isAccessibilityEnabled) "Enabled" else "Required for automation",
                    trailing = {
                        if (!isAccessibilityEnabled) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Enable")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                )

                SettingsItem(
                    icon = Icons.Default.PictureInPicture,
                    title = "Overlay Permission",
                    description = if (isOverlayGranted) "Granted" else "Required for drawing UI elements",
                    trailing = {
                        if (!isOverlayGranted) {
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            ) {
                                Text("Grant")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                )
                
                SettingsItem(
                    icon = Icons.Default.CameraAlt,
                    title = "Screen Capture Permission",
                    description = if (isScreenshotGranted) "Granted" else "Required for taking screenshots",
                    trailing = {
                        if (!isScreenshotGranted) {
                            Button(
                                onClick = {
                                    MainActivity.requestScreenshotPermission(context)
                                }
                            ) {
                                Text("Grant")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                )
            }
            
            Divider()
            
            SettingsSection(title = "Tools") {
                SettingsItem(
                    icon = Icons.Default.Image,
                    title = "View Screenshots",
                    description = "Browse and manage captured screenshots",
                    onClick = {
                        navController.navigate("screenshots_list")
                    }
                )
                SettingsItem(
                    icon = Icons.Default.TextFields,
                    title = "Test Text Recognition",
                    description = "Test ML Kit text recognition functionality",
                    onClick = {
                        navController.navigate("test_text_recognition")
                    }
                )
            }

            Divider()

            SettingsSection(title = "About") {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    description = "1.0.0"
                )

                SettingsItem(
                    icon = Icons.Default.Code,
                    title = "AutoBot RPA",
                    description = "Android automation tool"
                )
            }

            Divider()

            SettingsSection(title = "Help") {
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "How to Use",
                    description = "Grant accessibility permission to enable automation features"
                )

                SettingsItem(
                    icon = Icons.Default.Description,
                    title = "Documentation",
                    description = "Learn how to create automation scripts"
                )
            }
        }
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(clickableModifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            trailing?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotsListScreen(navController: NavHostController) {
    val context = LocalContext.current
    val screenshotsDir = context.filesDir.resolve("screenshots")
    var screenshotFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
    
    // 加载截图列表
    LaunchedEffect(Unit) {
        if (screenshotsDir.exists() && screenshotsDir.isDirectory) {
            screenshotFiles = screenshotsDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Screenshots") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        if (screenshotFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ImageNotSupported,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No screenshots yet",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Screenshots will appear here after capture",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    count = screenshotFiles.size,
                    key = { screenshotFiles[it].absolutePath }
                ) { index ->
                    val file = screenshotFiles[index]
                    ScreenshotThumbnail(
                        file = file,
                        onClick = { selectedImageIndex = index },
                        onDelete = {
                            file.delete()
                            screenshotFiles = screenshotFiles.filter { it != file }
                            // 如果删除的是当前查看的图片，关闭对话框
                            if (selectedImageIndex == index) {
                                selectedImageIndex = null
                            } else if (selectedImageIndex != null && selectedImageIndex!! > index) {
                                // 如果删除的是之前的图片，调整索引
                                selectedImageIndex = selectedImageIndex!! - 1
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 查看大图的对话框
    selectedImageIndex?.let { index ->
        if (index >= 0 && index < screenshotFiles.size) {
            val file = screenshotFiles[index]
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { selectedImageIndex = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("${index + 1}/${screenshotFiles.size} - ${file.name}") },
                                navigationIcon = {
                                    IconButton(onClick = { selectedImageIndex = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                actions = {
                                    // 上一张按钮
                                    IconButton(
                                        onClick = { if (index > 0) selectedImageIndex = index - 1 },
                                        enabled = index > 0
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Previous")
                                    }
                                    // 下一张按钮
                                    IconButton(
                                        onClick = { if (index < screenshotFiles.size - 1) selectedImageIndex = index + 1 },
                                        enabled = index < screenshotFiles.size - 1
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Next")
                                    }
                                    IconButton(
                                        onClick = {
                                            // 分享截图
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "image/png"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share Screenshot"))
                                        }
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = "Share")
                                    }
                                    IconButton(
                                        onClick = {
                                            // 删除截图
                                            file.delete()
                                            screenshotFiles = screenshotFiles.filter { it != file }
                                            if (screenshotFiles.isEmpty()) {
                                                selectedImageIndex = null
                                            } else if (index >= screenshotFiles.size) {
                                                selectedImageIndex = screenshotFiles.size - 1
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = file.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(modifier = Modifier.height(42.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenshotThumbnail(
    file: File,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bitmap = remember(file) { BitmapFactory.decodeFile(file.absolutePath) }
    
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestTextRecognitionScreen(navController: NavHostController) {
    val context = LocalContext.current
    
    // UI 状态
    var isInitialized by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ML Kit 文本识别测试") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 功能卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ML Kit 文本识别已集成",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Button(
                        onClick = { 
                            try {
                                // 初始化服务
                                TextRecognitionService.Companion.init(context)
                                isInitialized = true
                            } catch (e: Exception) {
                                android.util.Log.e("TestTextRecognition", "Init error", e)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("初始化 ML Kit 服务")
                    }
                }
            }
            
            // 状态显示
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = if (isInitialized) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                } else {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "状态",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isInitialized) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (isInitialized) {
                            "ML Kit 文本识别服务已就绪！"
                        } else {
                            "点击按钮初始化 ML Kit 服务"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isInitialized) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            
            // 如何使用
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "如何使用",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "1. 在脚本编辑器中添加 \"Find Text\" 动作\n" +
                                "2. 输入要查找的目标文字\n" +
                                "3. 配置超时和阈值参数\n" +
                                "4. 运行脚本进行测试\n" +
                                "5. 查看调试截图（会标注识别到的文字）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 主要功能
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "主要功能",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "• TextRecognitionService: ML Kit 文本识别服务\n" +
                                "• ScriptAction.FindText: 查找屏幕上的文字\n" +
                                "• ConditionType.TEXT_FOUND/TEXT_NOT_FOUND: 文字条件判断\n" +
                                "• 调试模式: 保存带有文字标注的截图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
