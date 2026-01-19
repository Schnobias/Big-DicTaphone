package com.bigdictaphone.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bigdictaphone.app.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToDoListScreen(
    viewModel: RecordingViewModel = viewModel(),
    onNavigateToRecording: (String) -> Unit = {}
) {
    val recordings by viewModel.recordings.collectAsState()
    var showCompleted by remember { mutableStateOf(false) }
    
    val actionItems = remember(recordings, showCompleted) {
        if (showCompleted) {
            viewModel.getAllActionItems()
        } else {
            viewModel.getIncompleteActionItems()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📋 To-Do List") },
                actions = {
                    IconButton(onClick = { showCompleted = !showCompleted }) {
                        Icon(
                            if (showCompleted) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (showCompleted) "Hide completed" else "Show completed"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (actionItems.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (showCompleted) "No action items yet" else "All done!",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Action items from your recordings will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary header
                item {
                    val total = viewModel.getAllActionItems().size
                    val completed = viewModel.getAllActionItems().count { it.second.completed }
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$total", style = MaterialTheme.typography.headlineMedium)
                                Text("Total", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$completed", style = MaterialTheme.typography.headlineMedium)
                                Text("Done", style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${total - completed}", style = MaterialTheme.typography.headlineMedium)
                                Text("Pending", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                }
                
                items(actionItems, key = { "${it.first.id}_${it.second.id}" }) { (recording, actionItem) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToRecording(recording.id) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Checkbox(
                                checked = actionItem.completed,
                                onCheckedChange = {
                                    viewModel.toggleActionItemComplete(recording.id, actionItem.id)
                                }
                            )
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = actionItem.task,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textDecoration = if (actionItem.completed) TextDecoration.LineThrough else null,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                Spacer(Modifier.height(4.dp))
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Assignee
                                    actionItem.assignee?.takeIf { it.isNotBlank() }?.let {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    // Deadline
                                    actionItem.deadline?.takeIf { it.isNotBlank() }?.let {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(4.dp))
                                
                                // Recording source
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${recording.title} • ${formatDate(recording.timestamp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
