package com.autobot.rpa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.autobot.rpa.service.AutomationEngine
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptExecutionScreen(
    viewModel: ScriptExecutionViewModel = hiltViewModel()
) {
    val scripts by viewModel.scripts.collectAsState()
    val selectedScript by viewModel.selectedScript.collectAsState()
    val executionState by viewModel.executionState.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.execute)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
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
                        scripts.forEach { script ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedScript?.id == script.id,
                                    onClick = { viewModel.selectScript(script) }
                                )
                                Text(
                                    text = script.name,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (executionState) {
                            is AutomationEngine.ExecutionState.Idle -> {
                                Button(
                                    onClick = { viewModel.startExecution() },
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
                            is AutomationEngine.ExecutionState.Completed -> {
                                Button(
                                    onClick = { viewModel.stopExecution() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reset")
                                }
                            }
                            is AutomationEngine.ExecutionState.Error -> {
                                Button(
                                    onClick = { viewModel.stopExecution() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Reset")
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
}
