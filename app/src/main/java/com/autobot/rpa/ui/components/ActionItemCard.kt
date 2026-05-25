package com.autobot.rpa.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.autobot.rpa.data.model.ScriptAction
import com.autobot.rpa.data.model.ConditionType

@Composable
fun ActionItemCard(
    action: ScriptAction,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val (icon, title, description) = getActionInfo(action)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(40.dp)
                )

                if (action is ScriptAction.FindImage && action.templatePath.isNotBlank()) {
                    val bitmap = remember(action.templatePath) {
                        try {
                            BitmapFactory.decodeFile(action.templatePath)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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

                Column {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun getActionInfo(action: ScriptAction): Triple<ImageVector, String, String> {
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
    }
}
