package com.autobot.rpa.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autobot.rpa.R
import com.autobot.rpa.MainActivity
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.service.AutomationEngine
import com.autobot.rpa.service.FloatingWindowService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptExecutionScreen(
    viewModel: ScriptExecutionViewModel = hiltViewModel()
) {
    val scripts by viewModel.scripts.collectAsState()
    val selectedScript by viewModel.selectedScript.collectAsState()
    val selectedRunMode by viewModel.selectedRunMode.collectAsState()
    val executionState by viewModel.executionState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }
    var showScreenshotDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.execute)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),

            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Select Script",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (scripts.isEmpty()) {
                    Text(
                        text = "No scripts available. Create one first!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedScript?.name ?: "Select a script",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Script") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            scripts.forEach { script ->
                                DropdownMenuItem(
                                    text = { Text(script.name) },
                                    onClick = {
                                        viewModel.selectScript(script)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Select Run Mode",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRunMode == AutomationEngine.RunMode.EXECUTE,
                            onClick = { viewModel.selectRunMode(AutomationEngine.RunMode.EXECUTE) }
                        )
                        Text(
                            text = "Execute",
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = selectedRunMode == AutomationEngine.RunMode.DEBUG,
                            onClick = { viewModel.selectRunMode(AutomationEngine.RunMode.DEBUG) }
                        )
                        Text(
                            text = "Debug",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (executionState) {
                is AutomationEngine.ExecutionState.Idle, 
                is AutomationEngine.ExecutionState.Completed, 
                is AutomationEngine.ExecutionState.Error -> {
                    Button(
                        onClick = { 
                            when {
                                !viewModel.isAccessibilityServiceEnabled() -> {
                                    showAccessibilityDialog = true
                                }
                                !viewModel.checkOverlayPermission(context) -> {
                                    showOverlayDialog = true
                                }
                                !viewModel.checkScreenshotPermission() -> {
                                    showScreenshotDialog = true
                                }
                                else -> {
                                    viewModel.openFloatingWindow()
                                    minimizeApp(context as Activity)
                                }
                            }
                        },
                        enabled = selectedScript != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run")
                    }
                }
                            is AutomationEngine.ExecutionState.Running -> {
                                Button(
                                    onClick = { viewModel.pauseExecution() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pause")
                                }
                                Button(
                                    onClick = { viewModel.stopExecution() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop")
                                }
                            }
                            is AutomationEngine.ExecutionState.Paused -> {
                                Button(
                                    onClick = { viewModel.resumeExecution() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Resume")
                                }
                                Button(
                                    onClick = { viewModel.stopExecution() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Stop")
                                }
                            }


                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (executionState) {
                                is AutomationEngine.ExecutionState.Running -> Icons.Default.Circle
                                is AutomationEngine.ExecutionState.Paused -> Icons.Default.Pause
                                is AutomationEngine.ExecutionState.Completed -> Icons.Default.CheckCircle
                                is AutomationEngine.ExecutionState.Error -> Icons.Default.Error
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = when (executionState) {
                                is AutomationEngine.ExecutionState.Running -> MaterialTheme.colorScheme.primary
                                is AutomationEngine.ExecutionState.Paused -> MaterialTheme.colorScheme.tertiary
                                is AutomationEngine.ExecutionState.Completed -> MaterialTheme.colorScheme.secondary
                                is AutomationEngine.ExecutionState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (executionState) {
                                is AutomationEngine.ExecutionState.Idle -> "Idle"
                                is AutomationEngine.ExecutionState.Running -> "Running..."
                                is AutomationEngine.ExecutionState.Paused -> "Paused"
                                is AutomationEngine.ExecutionState.Completed -> "Completed"
                                is AutomationEngine.ExecutionState.Error -> "Error: ${(executionState as? AutomationEngine.ExecutionState.Error)?.message}"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Logs",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = { viewModel.clearLogs() }) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    reverseLayout = true
                ) {
                    items(logs.reversed()) { log ->
                        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = dateFormat.format(Date(log.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(70.dp)
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (log.type) {
                                    AutomationEngine.LogType.SUCCESS -> MaterialTheme.colorScheme.secondary
                                    AutomationEngine.LogType.WARNING -> MaterialTheme.colorScheme.tertiary
                                    AutomationEngine.LogType.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDialog = false },
            title = { Text("需要无障碍服务权限") },
            text = { Text("脚本执行功能需要无障碍服务权限，请在设置中开启") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                        showAccessibilityDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showOverlayDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("脚本执行功能需要悬浮窗权限，请在设置中开启") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        context.startActivity(intent)
                        showOverlayDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    if (showScreenshotDialog) {
        AlertDialog(
            onDismissRequest = { showScreenshotDialog = false },
            title = { Text("需要截图权限") },
            text = { Text("脚本执行功能需要截图权限，请授予权限") },
            confirmButton = {
                TextButton(
                    onClick = {
                        MainActivity.requestScreenshotPermission(context)
                        showScreenshotDialog = false
                    }
                ) {
                    Text("授权")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScreenshotDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

private fun minimizeApp(activity: Activity) {
    activity.moveTaskToBack(true)
}
