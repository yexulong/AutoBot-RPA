package com.autobot.rpa.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    scriptId: Long,
    viewModel: ScriptEditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val script by viewModel.script.collectAsState()
    val actions by viewModel.actions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasChanges by viewModel.hasChanges.collectAsState()

    var showAddActionDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var scriptName by remember { mutableStateOf("") }
    var editingAction by remember { mutableStateOf<ScriptAction?>(null) }

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
                )
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
        is ScriptAction.LoopStart -> EditLoopStartDialog(action, onDismiss, onSave)
        is ScriptAction.LoopEnd -> EditLoopEndDialog(action, onDismiss, onSave)
        is ScriptAction.Condition -> EditConditionDialog(action, onDismiss, onSave)
        is ScriptAction.Comment -> EditCommentDialog(action, onDismiss, onSave)
    }
}

@Composable
fun EditTapDialog(
    action: ScriptAction.Tap,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Tap) -> Unit
) {
    val context = LocalContext.current
    var x by remember { mutableStateOf(action.x.toString()) }
    var y by remember { mutableStateOf(action.y.toString()) }
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
                    label = { Text("X Coordinate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y Coordinate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(x = newX, y = newY, duration = newDuration))
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
    var startX by remember { mutableStateOf(action.startX.toString()) }
    var startY by remember { mutableStateOf(action.startY.toString()) }
    var endX by remember { mutableStateOf(action.endX.toString()) }
    var endY by remember { mutableStateOf(action.endY.toString()) }
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
                    label = { Text("Start X") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startY,
                    onValueChange = { startY = it },
                    label = { Text("Start Y") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endX,
                    onValueChange = { endX = it },
                    label = { Text("End X") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endY,
                    onValueChange = { endY = it },
                    label = { Text("End Y") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(startX = newStartX, startY = newStartY, endX = newEndX, endY = newEndY, duration = newDuration))
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
    var x by remember { mutableStateOf(action.x.toString()) }
    var y by remember { mutableStateOf(action.y.toString()) }
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
                    label = { Text("X Coordinate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = y,
                    onValueChange = { y = it },
                    label = { Text("Y Coordinate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val newDuration = duration.toIntOrNull() ?: action.duration
                    onSave(action.copy(x = newX, y = newY, duration = newDuration))
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
    var templatePath by remember { mutableStateOf(action.templatePath) }
    var timeout by remember { mutableStateOf(action.timeout.toString()) }
    var saveResult by remember { mutableStateOf(action.saveResult) }
    var resultVarName by remember { mutableStateOf(action.resultVarName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Find Image") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = templatePath,
                    onValueChange = { templatePath = it },
                    label = { Text("Template Path") },
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveResult,
                        onCheckedChange = { saveResult = it }
                    )
                    Text("Save Result")
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
                    val newResultVarName = if (resultVarName.isBlank()) null else resultVarName
                    onSave(action.copy(
                        templatePath = templatePath,
                        timeout = newTimeout,
                        saveResult = saveResult,
                        resultVarName = newResultVarName
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConditionDialog(
    action: ScriptAction.Condition,
    onDismiss: () -> Unit,
    onSave: (ScriptAction.Condition) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(action.type) }
    var param1 by remember { mutableStateOf(action.param1) }
    var param2 by remember { mutableStateOf(action.param2) }
    var param3 by remember { mutableStateOf(action.param3) }

    val conditionTypes = listOf(
        ConditionType.IMAGE_FOUND,
        ConditionType.IMAGE_NOT_FOUND,
        ConditionType.COLOR_MATCH,
        ConditionType.COLOR_NOT_MATCH,
        ConditionType.ALWAYS_TRUE,
        ConditionType.ALWAYS_FALSE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Condition") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                OutlinedTextField(
                    value = param1,
                    onValueChange = { param1 = it },
                    label = { Text("Parameter 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = param2,
                    onValueChange = { param2 = it },
                    label = { Text("Parameter 2") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = param3,
                    onValueChange = { param3 = it },
                    label = { Text("Parameter 3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(action.copy(type = selectedType, param1 = param1, param2 = param2, param3 = param3))
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
                                "Screenshot" -> ScriptAction.Screenshot(id = java.util.UUID.randomUUID().toString(), order = 0, fileName = "screenshot")
                                "Find Image" -> ScriptAction.FindImage(id = java.util.UUID.randomUUID().toString(), order = 0, templatePath = "", timeout = 5000)
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
