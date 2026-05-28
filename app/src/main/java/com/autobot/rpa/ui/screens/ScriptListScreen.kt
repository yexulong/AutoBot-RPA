package com.autobot.rpa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.autobot.rpa.R
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    viewModel: ScriptListViewModel = hiltViewModel(),
    onNavigateToEditor: (Long) -> Unit,
    onNavigateToExecution: () -> Unit,
    onNavigateToGroups: () -> Unit,
    onBackPressed: ((() -> Boolean) -> Unit)
) {
    val isShowingScripts by viewModel.isShowingScripts.collectAsState()
    
    DisposableEffect(Unit) {
        onBackPressed {
            if (isShowingScripts) {
                viewModel.setIsShowingScripts(false)
                viewModel.selectGroup(null)
                true
            } else {
                false
            }
        }
        onDispose { }
    }
    val scripts by viewModel.filteredScripts.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope
    var showCreateDialog by remember { mutableStateOf(false) }
    var scriptToDelete by remember { mutableStateOf<Script?>(null) }
    var scriptToChangeGroup by remember { mutableStateOf<Script?>(null) }

    val currentGroupName = if (selectedGroupId == null) {
        stringResource(R.string.ungrouped)
    } else {
        groups.find { it.id == selectedGroupId }?.name ?: stringResource(R.string.ungrouped)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isShowingScripts) {
                        Text(currentGroupName)
                    } else {
                        Text(stringResource(R.string.scripts))
                    }
                },
                navigationIcon = {
                    if (isShowingScripts) {
                        IconButton(onClick = {
                            viewModel.setIsShowingScripts(false)
                            viewModel.selectGroup(null)
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!isShowingScripts) {
                        IconButton(onClick = onNavigateToGroups) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.groups))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            if (isShowingScripts) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_script))
                }
            }
        }
    ) { padding ->
        if (isShowingScripts) {
            // 显示某个分组的脚本列表
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (scripts.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_scripts),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scripts) { script ->
                            ScriptCard(
                                script = script,
                                groups = groups,
                                onClick = { onNavigateToEditor(script.id) },
                                onDelete = { scriptToDelete = script },
                                onExecute = {
                                    onNavigateToEditor(script.id)
                                },
                                onChangeGroup = { scriptToChangeGroup = script }
                            )
                        }
                    }
                }
            }
        } else {
            // 显示分组列表
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // "未分组"项
                        item {
                            GroupItem(
                                name = stringResource(R.string.ungrouped),
                                onClick = {
                                    viewModel.selectGroup(null)
                                    viewModel.setIsShowingScripts(true)
                                }
                            )
                        }
                        // 所有分组项
                        items(groups) { group ->
                            GroupItem(
                                name = group.name,
                                onClick = {
                                    viewModel.selectGroup(group.id)
                                    viewModel.setIsShowingScripts(true)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateScriptDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                lifecycleScope.launch {
                    viewModel.createNewScript(name)
                    showCreateDialog = false
                }
            }
        )
    }

    scriptToDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { scriptToDelete = null },
            title = { Text(stringResource(R.string.delete_script)) },
            text = { Text("Are you sure you want to delete '${script.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteScript(script)
                        scriptToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scriptToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    scriptToChangeGroup?.let { script ->
        ScriptGroupDialog(
            script = script,
            groups = groups,
            onDismiss = { scriptToChangeGroup = null },
            onSave = { groupId ->
                viewModel.updateScriptGroup(script, groupId)
                scriptToChangeGroup = null
            }
        )
    }
}

@Composable
fun ScriptCard(
    script: Script,
    groups: List<ScriptGroup>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onExecute: () -> Unit,
    onChangeGroup: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val groupName = script.groupId?.let { id ->
        groups.find { it.id == id }?.name
    } ?: stringResource(R.string.ungrouped)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = script.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (script.description.isNotEmpty()) {
                        Text(
                            text = script.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = onChangeGroup) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = stringResource(R.string.change_group),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onExecute) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Execute",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Actions: ${script.actions.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Runs: ${script.runCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                script.lastRunAt?.let {
                    Text(
                        text = "Last: ${dateFormat.format(Date(it))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CreateScriptDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_script)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.script_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ScriptGroupDialog(
    script: Script,
    groups: List<ScriptGroup>,
    onDismiss: () -> Unit,
    onSave: (Long?) -> Unit
) {
    var selectedGroupId by remember { mutableStateOf(script.groupId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.change_group)) },
        text = {
            Column {
                Text("${script.name}:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                RadioButtonGroup(
                    options = listOf(null) + groups.map { it.id },
                    selectedOption = selectedGroupId,
                    labels = listOf(stringResource(R.string.ungrouped)) + groups.map { it.name },
                    onOptionSelected = { selectedGroupId = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedGroupId) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RadioButtonGroup(
    options: List<Long?>,
    selectedOption: Long?,
    labels: List<String>,
    onOptionSelected: (Long?) -> Unit
) {
    Column {
        options.zip(labels).forEach { (option, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOptionSelected(option) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedOption == option,
                    onClick = { onOptionSelected(option) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label)
            }
        }
    }
}

@Composable
fun GroupItem(
    name: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
