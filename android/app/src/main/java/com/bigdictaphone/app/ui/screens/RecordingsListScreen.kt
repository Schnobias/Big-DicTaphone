package com.bigdictaphone.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigdictaphone.app.data.ProcessingStatus
import com.bigdictaphone.app.data.Recording
import com.bigdictaphone.app.data.CaptureStatus
import com.bigdictaphone.app.data.JobStatus
import com.bigdictaphone.app.viewmodel.RecordingViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsListScreen(
    viewModel: RecordingViewModel,
    onRecordingClick: (Recording) -> Unit
) {
    val recordings by viewModel.recordings.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isProcessing) {
                ProcessingBanner(message = processingMessage, onCancel = viewModel::cancelProcessing)
            }
            if (recordings.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recordings, key = { it.id }) { recording ->
                        RecordingCard(
                            recording = recording,
                            onClick = { onRecordingClick(recording) },
                            onDelete = { viewModel.deleteRecording(recording) }
                        )
                    }
                }
            }
            
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "No Recordings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Your voice notes and meeting recordings will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = getStatusColor(recording.status).copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = getStatusIcon(recording.status),
                            contentDescription = null,
                            tint = getStatusColor(recording.status),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Recording info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (recording.captureStatus == CaptureStatus.ACTIVE || recording.captureStatus == CaptureStatus.PAUSED) {
                    Text(if (recording.captureStatus == CaptureStatus.PAUSED) "Capture paused" else "Recording now", style = MaterialTheme.typography.labelSmall)
                } else if (recording.jobStatus == JobStatus.QUEUED) {
                    Text("Queued", style = MaterialTheme.typography.labelSmall)
                }
                recording.processingError?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(recording.language.flag)
                    
                    Text(
                        text = formatDate(recording.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    Text(
                        text = recording.formattedDuration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Status badge / delete button
            if (recording.jobStatus == JobStatus.RUNNING || recording.captureStatus == CaptureStatus.ACTIVE || recording.captureStatus == CaptureStatus.PAUSED) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording?") },
            text = { Text("This will permanently delete \"${recording.title}\" and cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ProcessingBanner(message: String, onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(message.ifEmpty { "Processing…" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

private fun getStatusIcon(status: ProcessingStatus) = when (status) {
    ProcessingStatus.RECORDED -> Icons.Default.Mic
    ProcessingStatus.TRANSCRIBING -> Icons.Default.TextFields
    ProcessingStatus.SUMMARIZING -> Icons.Default.AutoAwesome
    ProcessingStatus.COMPLETE -> Icons.Default.CheckCircle
    ProcessingStatus.FAILED -> Icons.Default.Error
}

private fun getStatusColor(status: ProcessingStatus) = when (status) {
    ProcessingStatus.RECORDED -> Color.Gray
    ProcessingStatus.TRANSCRIBING, ProcessingStatus.SUMMARIZING -> Color(0xFFFF9800)
    ProcessingStatus.COMPLETE -> Color(0xFF4CAF50)
    ProcessingStatus.FAILED -> Color(0xFFF44336)
}

private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val calendar = Calendar.getInstance()
    val todayCalendar = Calendar.getInstance()
    
    calendar.time = date
    todayCalendar.time = now
    
    return when {
        calendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR) -> {
            "Today ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)}"
        }
        calendar.get(Calendar.YEAR) == todayCalendar.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == todayCalendar.get(Calendar.DAY_OF_YEAR) - 1 -> {
            "Yesterday ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)}"
        }
        else -> {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)
        }
    }
}
