package com.autobot.rpa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autobot.rpa.R
import com.autobot.rpa.data.model.ScriptAction
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
    var showSaveDialog by remember { mutableStateOf(false) }
    var scriptName by remember { mutableStateOf("") }

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
                                onEdit = { /* TODO: Implement edit */ },
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
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(description) },
                        leadingContent = {
                            Icon(icon, contentDescription = null)
                        },
                        modifier = Modifier.clickable {
                            val action = when (name) {
                                "Tap" -> ScriptAction.Tap(x = 500, y = 500)
                                "Swipe" -> ScriptAction.Swipe(startX = 500, startY = 1000, endX = 500, endY = 500)
                                "Long Press" -> ScriptAction.LongPress(x = 500, y = 500)
                                "Text Input" -> ScriptAction.TextInput(text = "Hello")
                                "Key Press" -> ScriptAction.KeyPress(keyCode = 4)
                                "Delay" -> ScriptAction.Delay(milliseconds = 1000)
                                "Screenshot" -> ScriptAction.Screenshot(fileName = "screenshot")
                                "Find Image" -> ScriptAction.FindImage(templatePath = "", timeout = 5000)
                                "Loop Start" -> ScriptAction.LoopStart(times = 3)
                                "Loop End" -> ScriptAction.LoopEnd()
                                "Condition" -> ScriptAction.Condition(type = com.autobot.rpa.data.model.ConditionType.IMAGE_FOUND)
                                "Comment" -> ScriptAction.Comment(text = "Comment")
                                else -> return@ListItem
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
private fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { }
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

private fun Modifier.clickable(onClick: () -> Unit): Modifier = this.then(
    androidx.compose.foundation.clickable(onClick = onClick)
)
