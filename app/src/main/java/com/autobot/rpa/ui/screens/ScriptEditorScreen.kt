package com.autobot.rpa.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.Divider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autobot.rpa.R
import com.autobot.rpa.data.model.ScriptAction
import com.autobot.rpa.data.model.ConditionType
import com.autobot.rpa.service.CoordinateRecorderService
import com.autobot.rpa.ui.components.ActionItemCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: Long,
    viewModel: ScriptEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)? = null
) {
    val hasChanges by viewModel.hasChanges.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        onBackPressed?.invoke {
            if (hasChanges) {
                showSaveDialog = true
                true
            } else {
                false
            }
        }
        onDispose { }
    }
    val script by viewModel.script.collectAsState()
    val actions by viewModel.actions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val saveComplete by viewModel.saveComplete.collectAsState()

    var showAddActionDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var scriptName by remember { mutableStateOf("") }
    var editingAction by remember { mutableStateOf<ScriptAction?>(null) }

    LaunchedEffect(saveComplete) {
        if (saveComplete) {
            showSuccessDialog = true
            viewModel.resetSaveComplete()
        }
    }

    LaunchedEffect(script) {
        script?.let {
            scriptName = it.name
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scriptId > 0) script?.name ?: "Edit Script" else "New Script") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) {
                            showSaveDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (scriptName.isNotBlank()) {
                                viewModel.saveScript(scriptName)
                            }
                        },
                        enabled = scriptName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),

            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddActionDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_action))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = scriptName,
                            onValueChange = { scriptName = it },
                            label = { Text(stringResource(R.string.script_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text(
                            text = "Actions (${actions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    if (actions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "No actions yet. Tap + to add one.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        itemsIndexed(actions) { index, action ->
                            ActionItemCard(
                                action = action,
                                index = index,
                                onEdit = {
                                    editingAction = action
                                    showEditDialog = true
                                },
                                onDelete = { viewModel.removeAction(action.id) },
                                onMoveUp = { if (index > 0) viewModel.moveAction(index, index - 1) },
                                onMoveDown = { if (index < actions.size - 1) viewModel.moveAction(index, index + 1) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddActionDialog) {
        AddActionDialog(
            onDismiss = { showAddActionDialog = false },
            onActionSelected = { action ->
                viewModel.addAction(action)
                showAddActionDialog = false
            }
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Do you want to save your changes before leaving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (scriptName.isNotBlank()) {
                            viewModel.saveScript(scriptName)
                        }
                        showSaveDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showSaveDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text("Discard")
                    }
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    editingAction?.let { action ->
        if (showEditDialog) {
            EditActionDialog(
                action = action,
                onDismiss = {
                    showEditDialog = false
                    editingAction = null
                },
                onSave = { updatedAction ->
                    viewModel.updateAction(updatedAction)
                    showEditDialog = false
                    editingAction = null
                }
            )
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                onNavigateBack()
            },
            title = { Text("保存成功") },
            text = { Text("脚本已保存") },
            confirmButton = {
                TextButton(onClick = {
                    showSuccessDialog = false
                    onNavigateBack()
                }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
fun EditActionDialog(
    action: ScriptAction,
    onDismiss: () -> Unit,
    onSave: (ScriptAction) -> Unit
) {
    when (action) {
        is ScriptAction.Tap -> EditTapDialog(action, onDismiss, onSave)
        is ScriptAction.Swipe -> EditSwipeDialog(action, onDismiss, onSave)
        is ScriptAction.LongPress -> EditLongPressDialog(action, onDismiss, onSave)
        is ScriptAction.TextInput -> EditTextInputDialog(action, onDismiss, onSave)
        is ScriptAction.KeyPress -> EditKeyPressDialog(action, onDismiss, onSave)
        is ScriptAction.Delay -> EditDelayDialog(action, onDismiss, onSave)
        is ScriptAction.Screenshot -> EditScreenshotDialog(action, onDismiss, onSave)
        is ScriptAction.FindImage -> EditFindImageDialog(action, onDismiss, onSave)
        is ScriptAction.FindText -> EditFindTextDialog(action, onDismiss, onSave)
        is ScriptAction.LoopStart -> EditLoopStartDialog(action, onDismiss, onSave)
        is ScriptAction.LoopEnd -> EditLoopEndDialog(action, onDismiss, onSave)
        is ScriptAction.Condition -> EditConditionDialog(action, onDismiss, onSave)
        is ScriptAction.Comment -> EditCommentDialog(action, onDismiss, onSave)
        else -> {}
    }
}

@Composable
fun EditTapDialog(
    action: ScriptAction.Tap,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Tap) -> Unit
) {
    val context = LocalContext.current
    // 优先用字符串字段，没有的话用 Int 转成字符串
    var x by remember { mutableStateOf(action.xStr ?: action.x.toString()) }
    var y by remember { mutableStateOf(action.yStr ?: action.y.toString()) }
    var duration by remember { mutableStateOf(action.duration.toString()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("坐标记录功能需要悬浮窗权限，请在设置中开启") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        context.startActivity(intent)
                        showPermissionDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tap") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X Coordinate (数字或变量名如: var.x)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y Coordinate (数字或变量名如: var.y)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：可直接输入坐标数字，也可使用变量引用（例如: img.x 或 ${'$'}{img.y}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (checkOverlayPermission(context)) {
                            startCoordinateRecording(
                                context = context,
                                maxPoints = 1,
                                onCoordinatesConfirmed = { points ->
                                    if (points.isNotEmpty()) {
                                        x = points[0].x.toString()
                                        y = points[0].y.toString()
                                    }
                                },
                                onCoordinateCancelled = {}
                            )
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录坐标")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // 尝试解析数字，失败则保存为字符串字段
                    val newX = x.toIntOrNull() ?: action.x
                    val newY = y.toIntOrNull() ?: action.y
                    // 如果输入的不是数字，或者原本是字符串，就保留字符串
                    val newXStr = if (x.toIntOrNull() == null || action.xStr != null) x else null
                    val newYStr = if (y.toIntOrNull() == null || action.yStr != null) y else null
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(x = newX, y = newY, xStr = newXStr, yStr = newYStr, duration = newDuration))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditSwipeDialog(
    action: ScriptAction.Swipe,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Swipe) -> Unit
) {
    val context = LocalContext.current
    var startX by remember { mutableStateOf(action.startXStr ?: action.startX.toString()) }
    var startY by remember { mutableStateOf(action.startYStr ?: action.startY.toString()) }
    var endX by remember { mutableStateOf(action.endXStr ?: action.endX.toString()) }
    var endY by remember { mutableStateOf(action.endYStr ?: action.endY.toString()) }
    var duration by remember { mutableStateOf(action.duration.toString()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("坐标记录功能需要悬浮窗权限，请在设置中开启") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        context.startActivity(intent)
                        showPermissionDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Swipe") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startX,
                    onValueChange = { startX = it },
                    label = { Text("Start X (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startY,
                    onValueChange = { startY = it },
                    label = { Text("Start Y (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endX,
                    onValueChange = { endX = it },
                    label = { Text("End X (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endY,
                    onValueChange = { endY = it },
                    label = { Text("End Y (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：可直接输入坐标数字，也可使用变量引用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (checkOverlayPermission(context)) {
                            startCoordinateRecording(
                                context = context,
                                maxPoints = 2,
                                onCoordinatesConfirmed = { points ->
                                    if (points.size >= 2) {
                                        startX = points[0].x.toString()
                                        startY = points[0].y.toString()
                                        endX = points[1].x.toString()
                                        endY = points[1].y.toString()
                                    }
                                },
                                onCoordinateCancelled = {}
                            )
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录坐标 (两点)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newStartX = startX.toIntOrNull() ?: action.startX
                    val newStartY = startY.toIntOrNull() ?: action.startY
                    val newEndX = endX.toIntOrNull() ?: action.endX
                    val newEndY = endY.toIntOrNull() ?: action.endY
                    val newStartXStr = if (startX.toIntOrNull() == null || action.startXStr != null) startX else null
                    val newStartYStr = if (startY.toIntOrNull() == null || action.startYStr != null) startY else null
                    val newEndXStr = if (endX.toIntOrNull() == null || action.endXStr != null) endX else null
                    val newEndYStr = if (endY.toIntOrNull() == null || action.endYStr != null) endY else null
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(
                        startX = newStartX, startY = newStartY, 
                        endX = newEndX, endY = newEndY,
                        startXStr = newStartXStr, startYStr = newStartYStr,
                        endXStr = newEndXStr, endYStr = newEndYStr,
                        duration = newDuration
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditLongPressDialog(
    action: ScriptAction.LongPress,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.LongPress) -> Unit
) {
    val context = LocalContext.current
    var x by remember { mutableStateOf(action.xStr ?: action.x.toString()) }
    var y by remember { mutableStateOf(action.yStr ?: action.y.toString()) }
    var duration by remember { mutableStateOf(action.duration.toString()) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要悬浮窗权限") },
            text = { Text("坐标记录功能需要悬浮窗权限，请在设置中开启") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                        context.startActivity(intent)
                        showPermissionDialog = false
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Long Press") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = x,
                    onValueChange = { x = it },
                    label = { Text("X Coordinate (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y Coordinate (数字或变量名)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：可直接输入坐标数字，也可使用变量引用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        if (checkOverlayPermission(context)) {
                            startCoordinateRecording(
                                context = context,
                                maxPoints = 1,
                                onCoordinatesConfirmed = { points ->
                                    if (points.isNotEmpty()) {
                                        x = points[0].x.toString()
                                        y = points[0].y.toString()
                                    }
                                },
                                onCoordinateCancelled = {}
                            )
                        } else {
                            showPermissionDialog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记录坐标")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newX = x.toIntOrNull() ?: action.x
                    val newY = y.toIntOrNull() ?: action.y
                    val newXStr = if (x.toIntOrNull() == null || action.xStr != null) x else null
                    val newYStr = if (y.toIntOrNull() == null || action.yStr != null) y else null
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(x = newX, y = newY, xStr = newXStr, yStr = newYStr, duration = newDuration))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTextInputDialog(
    action: ScriptAction.TextInput,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.TextInput) -> Unit
) {
    var text by remember { mutableStateOf(action.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Text Input") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(action.copy(text = text))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditKeyPressDialog(
    action: ScriptAction.KeyPress,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.KeyPress) -> Unit
) {
    var keyCode by remember { mutableStateOf(action.keyCode.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Key Press") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = keyCode,
                    onValueChange = { keyCode = it },
                    label = { Text("Key Code") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Key codes: 4=Back, 3=Home, 66=Enter, 24=Vol+, 25=Vol-\nNote: System keys (Home, Power) require special accessibility permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newKeyCode = keyCode.toIntOrNull() ?: action.keyCode
                    onSave(action.copy(keyCode = newKeyCode))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditDelayDialog(
    action: ScriptAction.Delay,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Delay) -> Unit
) {
    var milliseconds by remember { mutableStateOf(action.milliseconds.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Delay") },
        text = {
            OutlinedTextField(
                value = milliseconds,
                onValueChange = { milliseconds = it },
                label = { Text("Milliseconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newMs = milliseconds.toIntOrNull() ?: action.milliseconds
                    onSave(action.copy(milliseconds = newMs))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditScreenshotDialog(
    action: ScriptAction.Screenshot,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Screenshot) -> Unit
) {
    var fileName by remember { mutableStateOf(action.fileName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Screenshot") },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(action.copy(fileName = fileName))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditFindImageDialog(
    action: ScriptAction.FindImage,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.FindImage) -> Unit
) {
    val context = LocalContext.current
    val screenshotsDir = context.filesDir.resolve("screenshots")
    val scope = rememberCoroutineScope()
    
    var templatePath by remember { mutableStateOf(action.templatePath) }
    var timeout by remember { mutableStateOf(action.timeout.toString()) }
    var threshold by remember { mutableStateOf(action.threshold.toString()) }
    var saveResult by remember { mutableStateOf(action.saveResult) }
    var debugMode by remember { mutableStateOf(action.debugMode) }
    var resultVarName by remember { mutableStateOf(action.resultVarName ?: "") }
    var showScreenshotPicker by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var screenshotFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    
    // 刷新截图列表函数
    val refreshScreenshots = {
        if (screenshotsDir.exists() && screenshotsDir.isDirectory) {
            screenshotFiles = screenshotsDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        }
    }
    
    // 文件选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                copyImageToScreenshots(context, it)?.let { file ->
                    templatePath = file.absolutePath
                    refreshScreenshots()
                }
            }
        }
    }
    
    // 加载截图列表
    LaunchedEffect(Unit) {
        refreshScreenshots()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Find Image") },
        text = {
            val scrollState = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                OutlinedTextField(
                    value = templatePath,
                    onValueChange = { templatePath = it },
                    label = { Text("Template Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (templatePath.isNotBlank()) {
                    val bitmap = remember(templatePath) {
                        try {
                            BitmapFactory.decodeFile(templatePath)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Template preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.BrokenImage,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSourceDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("选择图片")
                    }
                    Button(
                        onClick = { refreshScreenshots() },
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                }
                Text(
                    text = "提示：选择已有的截图，或者从手机存储中导入图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text("Timeout (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Threshold (0.0-1.0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：值越大匹配越严格，默认 0.7",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveResult,
                        onCheckedChange = { saveResult = it }
                    )
                    Text("Save Result")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = debugMode,
                        onCheckedChange = { debugMode = it }
                    )
                    Text("Debug Mode")
                }
                OutlinedTextField(
                    value = resultVarName,
                    onValueChange = { resultVarName = it },
                    label = { Text("Result Variable Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newTimeout = timeout.toIntOrNull() ?: action.timeout
                    val newThreshold = threshold.toDoubleOrNull() ?: action.threshold
                    val newResultVarName = if (resultVarName.isBlank()) null else resultVarName
                    onSave(action.copy(
                        templatePath = templatePath,
                        timeout = newTimeout,
                        threshold = newThreshold,
                        saveResult = saveResult,
                        resultVarName = newResultVarName,
                        debugMode = debugMode
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // 选择来源对话框
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("选择图片来源") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showSourceDialog = false
                            showScreenshotPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从截图中选择")
                    }
                    Button(
                        onClick = {
                            showSourceDialog = false
                            pickImageLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从手机存储选择")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {}
        )
    }
    
    // 截图选择对话框
    if (showScreenshotPicker) {
        ScreenshotPickerDialog(
            screenshotFiles = screenshotFiles,
            onDismiss = { showScreenshotPicker = false },
            onScreenshotSelected = { file ->
                templatePath = file.absolutePath
                showScreenshotPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotPickerDialog(
    screenshotFiles: List<java.io.File>,
    onDismiss: () -> Unit,
    onScreenshotSelected: (java.io.File) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模板图片") },
        text = {
            if (screenshotFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无截图，请先使用 Screenshot 动作截图")
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        count = screenshotFiles.size,
                        key = { screenshotFiles[it].absolutePath }
                    ) { index ->
                        val file = screenshotFiles[index]
                        val bitmap = remember(file) {
                            try {
                                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onScreenshotSelected(file) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = file.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun EditLoopStartDialog(
    action: ScriptAction.LoopStart,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.LoopStart) -> Unit
) {
    var times by remember { mutableStateOf(action.times.toString()) }
    var infinite by remember { mutableStateOf(action.infinite) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Loop Start") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = infinite,
                        onCheckedChange = { infinite = it }
                    )
                    Text("Infinite Loop")
                }
                if (!infinite) {
                    OutlinedTextField(
                        value = times,
                        onValueChange = { times = it },
                        label = { Text("Loop Times") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newTimes = if (infinite) -1 else (times.toIntOrNull() ?: action.times)
                    onSave(action.copy(times = newTimes, infinite = infinite))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditLoopEndDialog(
    action: ScriptAction.LoopEnd,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.LoopEnd) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loop End") },
        text = {
            Text("This action marks the end of a loop.")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
fun EditFindTextDialog(
    action: ScriptAction.FindText,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.FindText) -> Unit
) {
    var targetText by remember { mutableStateOf(action.targetText) }
    var timeout by remember { mutableStateOf(action.timeout.toString()) }
    var threshold by remember { mutableStateOf(action.threshold.toString()) }
    var saveResult by remember { mutableStateOf(action.saveResult) }
    var debugMode by remember { mutableStateOf(action.debugMode) }
    var resultVarName by remember { mutableStateOf(action.resultVarName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Find Text") },
        text = {
            val scrollState = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it },
                    label = { Text("Timeout (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Threshold (0.0-1.0)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "提示：值越大匹配越严格，默认 0.8",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveResult,
                        onCheckedChange = { saveResult = it }
                    )
                    Text("Save Result")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = debugMode,
                        onCheckedChange = { debugMode = it }
                    )
                    Text("Debug Mode")
                }
                OutlinedTextField(
                    value = resultVarName,
                    onValueChange = { resultVarName = it },
                    label = { Text("Result Variable Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newTimeout = timeout.toIntOrNull() ?: action.timeout
                    val newThreshold = threshold.toDoubleOrNull() ?: action.threshold
                    val newResultVarName = if (resultVarName.isBlank()) null else resultVarName
                    onSave(action.copy(
                        targetText = targetText,
                        timeout = newTimeout,
                        threshold = newThreshold,
                        saveResult = saveResult,
                        resultVarName = newResultVarName,
                        debugMode = debugMode
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConditionDialog(
    action: ScriptAction.Condition,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Condition) -> Unit
) {
    val context = LocalContext.current
    val screenshotsDir = context.filesDir.resolve("screenshots")
    val scope = rememberCoroutineScope()
    
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(action.type) }
    var param1 by remember { mutableStateOf(action.param1) }
    var param2 by remember { mutableStateOf(action.param2) }
    var param3 by remember { mutableStateOf(action.param3) }
    val trueBranch = remember { mutableStateListOf<ScriptAction>().apply { addAll(action.trueBranch) } }
    val falseBranch = remember { mutableStateListOf<ScriptAction>().apply { addAll(action.falseBranch) } }
    var showScreenshotPicker by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var screenshotFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showAddActionToBranchDialog by remember { mutableStateOf<String?>(null) } // "true" or "false"
    var editingBranchAction by remember { mutableStateOf<Pair<ScriptAction, String>?>(null) } // (action, branchName)
    
    // 刷新截图列表函数
    val refreshScreenshots = {
        if (screenshotsDir.exists() && screenshotsDir.isDirectory) {
            screenshotFiles = screenshotsDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        }
    }
    
    // 文件选择器
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                copyImageToScreenshots(context, it)?.let { file ->
                    param1 = file.absolutePath
                    refreshScreenshots()
                }
            }
        }
    }
    
    // 加载截图列表
    LaunchedEffect(Unit) {
        refreshScreenshots()
    }

    val conditionTypes = listOf(
        ConditionType.IMAGE_FOUND,
        ConditionType.IMAGE_NOT_FOUND,
        ConditionType.TEXT_FOUND,
        ConditionType.TEXT_NOT_FOUND,
        ConditionType.COLOR_MATCH,
        ConditionType.COLOR_NOT_MATCH,
        ConditionType.ALWAYS_TRUE,
        ConditionType.ALWAYS_FALSE
    )
    
    val isImageCondition = selectedType == ConditionType.IMAGE_FOUND || selectedType == ConditionType.IMAGE_NOT_FOUND
    val isTextCondition = selectedType == ConditionType.TEXT_FOUND || selectedType == ConditionType.TEXT_NOT_FOUND

    // 向分支添加动作
    val addActionToBranch = { branchName: String, newAction: ScriptAction ->
        when (branchName) {
            "true" -> trueBranch.add(newAction)
            "false" -> falseBranch.add(newAction)
        }
        showAddActionToBranchDialog = null
    }
    
    // 更新分支中的动作
    val updateActionInBranch = { branchName: String, oldAction: ScriptAction, newAction: ScriptAction ->
        when (branchName) {
            "true" -> {
                val index = trueBranch.indexOfFirst { it.id == oldAction.id }
                if (index != -1) trueBranch[index] = newAction
            }
            "false" -> {
                val index = falseBranch.indexOfFirst { it.id == oldAction.id }
                if (index != -1) falseBranch[index] = newAction
            }
        }
        editingBranchAction = null
    }
    
    // 从分支删除动作
    val deleteActionFromBranch = { branchName: String, actionToDelete: ScriptAction ->
        when (branchName) {
            "true" -> trueBranch.remove(actionToDelete)
            "false" -> falseBranch.remove(actionToDelete)
        }
    }
    
    // 移动分支中的动作
    val moveActionInBranch = { branchName: String, fromIndex: Int, toIndex: Int ->
        when (branchName) {
            "true" -> {
                if (toIndex >= 0 && toIndex < trueBranch.size) {
                    val item = trueBranch.removeAt(fromIndex)
                    trueBranch.add(toIndex, item)
                }
            }
            "false" -> {
                if (toIndex >= 0 && toIndex < falseBranch.size) {
                    val item = falseBranch.removeAt(fromIndex)
                    falseBranch.add(toIndex, item)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Condition") },
        text = {
            val scrollState = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                // 条件类型和参数配置
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedType.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Condition Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        conditionTypes.forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                                },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                if (isImageCondition) {
                    OutlinedTextField(
                        value = param1,
                        onValueChange = { param1 = it },
                        label = { Text("Template Path (Parameter 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showSourceDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("选择图片")
                        }
                        Button(
                            onClick = { refreshScreenshots() },
                            modifier = Modifier.width(IntrinsicSize.Min)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                    }
                    Text(
                        text = "Parameter 2：相似度阈值 (默认 0.8)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (isTextCondition) {
                    OutlinedTextField(
                        value = param1,
                        onValueChange = { param1 = it },
                        label = { Text("Target Text (Parameter 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Parameter 2：相似度阈值 (默认 0.8)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = param1,
                        onValueChange = { param1 = it },
                        label = { Text("Parameter 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                OutlinedTextField(
                    value = param2,
                    onValueChange = { param2 = it },
                    label = { 
                        Text(if (isImageCondition) "Similarity Threshold (0.0-1.0)" else "Parameter 2") 
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isImageCondition) {
                    OutlinedTextField(
                        value = param3,
                        onValueChange = { param3 = it },
                        label = { Text("Parameter 3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Divider()
                
                // True 分支配置
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "True Branch",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { showAddActionToBranchDialog = "true" },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add action to true branch",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                        
                        if (trueBranch.isEmpty()) {
                            Text(
                                text = "No actions in true branch. Click + to add.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            trueBranch.forEachIndexed { index, branchAction ->
                                BranchActionItem(
                                    action = branchAction,
                                    index = index,
                                    branchName = "true",
                                    onEdit = { editingBranchAction = Pair(branchAction, "true") },
                                    onDelete = { deleteActionFromBranch("true", branchAction) },
                                    onMoveUp = { if (index > 0) moveActionInBranch("true", index, index - 1) },
                                    onMoveDown = { if (index < trueBranch.size - 1) moveActionInBranch("true", index, index + 1) }
                                )
                            }
                        }
                    }
                }
                
                // False 分支配置
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "False Branch",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { showAddActionToBranchDialog = "false" },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add action to false branch",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                        
                        if (falseBranch.isEmpty()) {
                            Text(
                                text = "No actions in false branch. Click + to add.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            falseBranch.forEachIndexed { index, branchAction ->
                                BranchActionItem(
                                    action = branchAction,
                                    index = index,
                                    branchName = "false",
                                    onEdit = { editingBranchAction = Pair(branchAction, "false") },
                                    onDelete = { deleteActionFromBranch("false", branchAction) },
                                    onMoveUp = { if (index > 0) moveActionInBranch("false", index, index - 1) },
                                    onMoveDown = { if (index < falseBranch.size - 1) moveActionInBranch("false", index, index + 1) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(action.copy(
                        type = selectedType,
                        param1 = param1,
                        param2 = param2,
                        param3 = param3,
                        trueBranch = trueBranch.toList(),
                        falseBranch = falseBranch.toList()
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // 选择来源对话框
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("选择图片来源") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            showSourceDialog = false
                            showScreenshotPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从截图中选择")
                    }
                    Button(
                        onClick = {
                            showSourceDialog = false
                            pickImageLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从手机存储选择")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) {
                    Text("取消")
                }
            },
            confirmButton = {}
        )
    }
    
    // 截图选择对话框
    if (showScreenshotPicker) {
        ScreenshotPickerDialog(
            screenshotFiles = screenshotFiles,
            onDismiss = { showScreenshotPicker = false },
            onScreenshotSelected = { file ->
                param1 = file.absolutePath
                showScreenshotPicker = false
            }
        )
    }
    
    // 向分支添加动作对话框
    showAddActionToBranchDialog?.let { branchName ->
        AddActionDialog(
            onDismiss = { showAddActionToBranchDialog = null },
            onActionSelected = { newAction -> addActionToBranch(branchName, newAction) }
        )
    }
    
    // 编辑分支动作对话框
    editingBranchAction?.let { (actionToEdit, branchName) ->
        EditActionDialog(
            action = actionToEdit,
            onDismiss = { editingBranchAction = null },
            onSave = { newAction -> updateActionInBranch(branchName, actionToEdit, newAction) }
        )
    }
}

// 分支动作项组件
@Composable
fun BranchActionItem(
    action: ScriptAction,
    index: Int,
    branchName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (icon, title, description) = getActionInfo(action)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}.",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(32.dp)
            )
            
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move up",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move down",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun EditCommentDialog(
    action: ScriptAction.Comment,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Comment) -> Unit
) {
    var text by remember { mutableStateOf(action.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Comment") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Comment") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(action.copy(text = text))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddActionDialog(
    onDismiss: () -> Unit,
    onActionSelected: (ScriptAction) -> Unit
) {
    val actions = listOf(
        Triple("Tap", Icons.Default.TouchApp, "Perform a tap at coordinates"),
        Triple("Swipe", Icons.Default.Swipe, "Perform a swipe gesture"),
        Triple("Long Press", Icons.Default.TouchApp, "Perform a long press"),
        Triple("Text Input", Icons.Default.TextFields, "Input text"),
        Triple("Key Press", Icons.Default.Keyboard, "Press a key"),
        Triple("Delay", Icons.Default.Timer, "Wait for a duration"),
        Triple("Screenshot", Icons.Default.CameraAlt, "Take a screenshot"),
        Triple("Find Image", Icons.Default.ImageSearch, "Find an image on screen"),
        Triple("Find Text", Icons.Default.TextFields, "Find text on screen"),
        Triple("Loop Start", Icons.Default.Loop, "Start a loop"),
        Triple("Loop End", Icons.Default.Loop, "End a loop"),
        Triple("Condition", Icons.Default.CallSplit, "Conditional branch"),
        Triple("Comment", Icons.Default.Comment, "Add a comment")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_action)) },
        text = {
            LazyColumn {
                items(actions.size) { index ->
                    val (name, icon, description) = actions[index]
                    ActionListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(description) },
                        leadingContent = {
                            Icon(icon, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            val action = when (name) {
                                "Tap" -> ScriptAction.Tap(id = java.util.UUID.randomUUID().toString(), order = 0, x = 500, y = 500)
                                "Swipe" -> ScriptAction.Swipe(id = java.util.UUID.randomUUID().toString(), order = 0, startX = 500, startY = 1000, endX = 500, endY = 500)
                                "Long Press" -> ScriptAction.LongPress(id = java.util.UUID.randomUUID().toString(), order = 0, x = 500, y = 500)
                                "Text Input" -> ScriptAction.TextInput(id = java.util.UUID.randomUUID().toString(), order = 0, text = "Hello")
                                "Key Press" -> ScriptAction.KeyPress(id = java.util.UUID.randomUUID().toString(), order = 0, keyCode = 4)
                                "Delay" -> ScriptAction.Delay(id = java.util.UUID.randomUUID().toString(), order = 0, milliseconds = 1000)
                                "Screenshot" -> ScriptAction.Screenshot(id = java.util.UUID.randomUUID().toString(), order = 0, fileName = "")
                                "Find Image" -> ScriptAction.FindImage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    order = 0,
                                    templatePath = "",
                                    timeout = 5000,
                                    threshold = 0.7
                                )
                                "Find Text" -> ScriptAction.FindText(
                                    id = java.util.UUID.randomUUID().toString(),
                                    order = 0,
                                    targetText = "Text to find",
                                    timeout = 5000,
                                    threshold = 0.8
                                )
                                "Loop Start" -> ScriptAction.LoopStart(id = java.util.UUID.randomUUID().toString(), order = 0, times = 3)
                                "Loop End" -> ScriptAction.LoopEnd(id = java.util.UUID.randomUUID().toString(), order = 0)
                                "Condition" -> ScriptAction.Condition(id = java.util.UUID.randomUUID().toString(), order = 0, type = com.autobot.rpa.data.model.ConditionType.IMAGE_FOUND)
                                "Comment" -> ScriptAction.Comment(id = java.util.UUID.randomUUID().toString(), order = 0, text = "Comment")
                                else -> return@clickable
                            }
                            onActionSelected(action)
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ActionListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingContent?.let {
            it()
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            headlineContent()
            supportingContent?.invoke()
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

private fun startCoordinateRecording(
    context: Context,
    maxPoints: Int,
    onCoordinatesConfirmed: (List<Point>) -> Unit,
    onCoordinateCancelled: () -> Unit
) {
    val listener = object : CoordinateRecorderService.Companion.OnCoordinateRecordedListener {
        override fun onCoordinatesConfirmed(points: List<Point>) {
            onCoordinatesConfirmed(points)
        }

        override fun onCoordinateCancelled() {
            onCoordinateCancelled()
        }
    }
    CoordinateRecorderService.setOnCoordinateRecordedListener(listener)
    CoordinateRecorderService.startService(context, maxPoints)
}

// 辅助函数：从Uri复制图片到screenshots目录
private suspend fun copyImageToScreenshots(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        val screenshotsDir = File(context.filesDir, "screenshots").apply {
            if (!exists()) mkdirs()
        }
        
        // 获取文件名
        val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}.png"
        
        val destinationFile = File(screenshotsDir, fileName)
        
        // 复制文件
        FileOutputStream(destinationFile).use { output ->
            inputStream.copyTo(output)
        }
        
        inputStream.close()
        destinationFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 从Uri获取文件名
private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    result = it.getString(nameIndex)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1) {
            result = result?.substring(cut!! + 1)
        }
    }
    return result
}

// 获取动作信息（复制自 ActionItemCard.kt）
private fun getActionInfo(action: ScriptAction): Triple<androidx.compose.ui.graphics.vector.ImageVector, String, String> {
    return when (action) {
        is ScriptAction.Tap -> Triple(
            Icons.Default.TouchApp,
            "Tap",
            "Position: (${action.x}, ${action.y})"
        )
        is ScriptAction.Swipe -> Triple(
            Icons.Default.Swipe,
            "Swipe",
            "From (${action.startX}, ${action.startY}) to (${action.endX}, ${action.endY})"
        )
        is ScriptAction.LongPress -> Triple(
            Icons.Default.TouchApp,
            "Long Press",
            "Position: (${action.x}, ${action.y}), Duration: ${action.duration}ms"
        )
        is ScriptAction.TextInput -> Triple(
            Icons.Default.TextFields,
            "Text Input",
            "\"${action.text}\""
        )
        is ScriptAction.KeyPress -> Triple(
            Icons.Default.Keyboard,
            "Key Press",
            "KeyCode: ${action.keyCode}"
        )
        is ScriptAction.Delay -> Triple(
            Icons.Default.Timer,
            "Delay",
            "${action.milliseconds}ms"
        )
        is ScriptAction.Screenshot -> Triple(
            Icons.Default.CameraAlt,
            "Screenshot",
            action.fileName
        )
        is ScriptAction.FindImage -> Triple(
            Icons.Default.ImageSearch,
            "Find Image",
            "Timeout: ${action.timeout}ms"
        )
        is ScriptAction.FindText -> Triple(
            Icons.Default.TextFields,
            "Find Text",
            "Target: \"${action.targetText}\""
        )
        is ScriptAction.LoopStart -> Triple(
            Icons.Default.Loop,
            "Loop Start",
            if (action.infinite) "Infinite loop" else "${action.times} times"
        )
        is ScriptAction.LoopEnd -> Triple(
            Icons.Default.Loop,
            "Loop End",
            "End of loop"
        )
        is ScriptAction.Condition -> Triple(
            Icons.Default.CallSplit,
            "Condition",
            when (action.type) {
                ConditionType.IMAGE_FOUND -> "If image found"
                ConditionType.IMAGE_NOT_FOUND -> "If image not found"
                ConditionType.TEXT_FOUND -> "If text found"
                ConditionType.TEXT_NOT_FOUND -> "If text not found"
                ConditionType.COLOR_MATCH -> "If color matches"
                ConditionType.COLOR_NOT_MATCH -> "If color doesn't match"
                ConditionType.ALWAYS_TRUE -> "Always true"
                ConditionType.ALWAYS_FALSE -> "Always false"
            }
        )
        is ScriptAction.Comment -> Triple(
            Icons.Default.Comment,
            "Comment",
            action.text
        )
        is ScriptAction.SetVariable -> Triple(
            Icons.Default.Edit,
            "Set Variable",
            "${action.varName} = ${action.varValue}"
        )
    }
}
