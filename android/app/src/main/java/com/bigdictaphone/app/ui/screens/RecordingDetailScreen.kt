package com.bigdictaphone.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigdictaphone.app.data.ActionItem
import com.bigdictaphone.app.data.ProcessingStatus
import com.bigdictaphone.app.data.Recording
import com.bigdictaphone.app.services.AudioRecorderService
import com.bigdictaphone.app.viewmodel.RecordingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingDetailScreen(
    recordingId: String,
    viewModel: RecordingViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsState()
    val recording = remember(recordings, recordingId) { 
        viewModel.getRecording(recordingId) 
    }
    
    val userEmail by viewModel.userEmail.collectAsState(initial = "")
    val stakeholders by viewModel.stakeholders.collectAsState(initial = emptyList())
    
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showTranscription by remember { mutableStateOf(false) }
    
    // Cleanup media player
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    // Update playback progress
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    playbackProgress = player.currentPosition.toFloat() / player.duration.toFloat()
                } else {
                    isPlaying = false
                    playbackProgress = 0f
                }
            }
            delay(100)
        }
    }
    
    if (recording == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Recording not found")
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recording.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reprocessRecording(recording) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reprocess")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with recording info
            HeaderCard(recording = recording)
            
            // Audio player
            AudioPlayerCard(
                recording = recording,
                isPlaying = isPlaying,
                progress = playbackProgress,
                onPlayPause = {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            val file = viewModel.audioRecorder.getRecordingFile(recording.audioFileName)
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                prepare()
                                start()
                            }
                        } else {
                            mediaPlayer?.start()
                        }
                        isPlaying = true
                    }
                }
            )
            
            // Content based on status
            when (recording.status) {
                ProcessingStatus.TRANSCRIBING, ProcessingStatus.SUMMARIZING -> {
                    ProcessingCard(status = recording.status)
                }
                ProcessingStatus.FAILED -> {
                    FailedCard(
                        onRetry = { viewModel.reprocessRecording(recording) }
                    )
                }
                ProcessingStatus.COMPLETE -> {
                    recording.summary?.let { summary ->
                        // Key Points
                        if (summary.keyPoints.isNotEmpty()) {
                            SummarySection(
                                title = "Key Points",
                                icon = Icons.Default.Lightbulb,
                                iconTint = Color(0xFFFFC107)
                            ) {
                                summary.keyPoints.forEach { point ->
                                    BulletPoint(text = point)
                                }
                            }
                        }
                        
                        // Action Items
                        if (summary.actionItems.isNotEmpty()) {
                            SummarySection(
                                title = "Action Items",
                                icon = Icons.Default.CheckCircle,
                                iconTint = Color(0xFF4CAF50)
                            ) {
                                summary.actionItems.forEach { item ->
                                    ActionItemCard(item = item)
                                }
                            }
                        }
                        
                        // Future Points
                        if (summary.futurePoints.isNotEmpty()) {
                            SummarySection(
                                title = "Future Discussion",
                                icon = Icons.Default.Event,
                                iconTint = Color(0xFF2196F3)
                            ) {
                                summary.futurePoints.forEach { point ->
                                    BulletPoint(text = point, icon = Icons.Default.ArrowForward)
                                }
                            }
                        }
                    }
                    
                    // Transcription (collapsible)
                    recording.transcription?.let { transcription ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.TextSnippet,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Full Transcription", fontWeight = FontWeight.SemiBold)
                                    }
                                    
                                    IconButton(onClick = { showTranscription = !showTranscription }) {
                                        Icon(
                                            if (showTranscription) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (showTranscription) "Collapse" else "Expand"
                                        )
                                    }
                                }
                                
                                if (showTranscription) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = transcription,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    
                    // Personal Summary Card
                    PersonalSummaryCard(
                        recording = recording,
                        userEmail = userEmail,
                        onSend = { email, content ->
                            viewModel.sendEmail(
                                email,
                                "Meeting Summary: ${recording.title}",
                                content
                            )
                        },
                        initialBody = remember(recording) {
                            viewModel.emailService.createPersonalSummaryContent(recording)
                        }
                    )
                    
                    // Stakeholder Cards
                    if (stakeholders.isNotEmpty()) {
                        Text(
                            "Stakeholder Updates",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        
                        stakeholders.forEach { stakeholder ->
                            StakeholderEmailCard(
                                stakeholder = stakeholder,
                                recording = recording,
                                onSend = { email, content ->
                                    viewModel.sendEmail(
                                        email,
                                        "Status Update: ${recording.title}",
                                        content
                                    )
                                },
                                initialBody = remember(recording) {
                                    viewModel.emailService.createManagementUpdateContent(recording)
                                }
                            )
                        }
                    }
                }
                ProcessingStatus.RECORDED -> {
                    WaitingCard(
                        onProcess = { viewModel.reprocessRecording(recording) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(recording: Recording) {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    "${recording.language.flag} ${recording.language.displayName}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    dateFormat.format(Date(recording.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun AudioPlayerCard(
    recording: Recording,
    isPlaying: Boolean,
    progress: Float,
    onPlayPause: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    AudioRecorderService.formatTime((progress * recording.duration).toLong()),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    recording.formattedDuration,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            
            // Play button
            Button(
                onClick = onPlayPause,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) "Pause" else "Play Recording")
            }
        }
    }
}

@Composable
private fun SummarySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold)
            }
            
            Spacer(Modifier.height(12.dp))
            
            content()
        }
    }
}

@Composable
private fun BulletPoint(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Circle
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier
                .size(8.dp)
                .padding(top = 6.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionItemCard(item: ActionItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.task, style = MaterialTheme.typography.bodyMedium)
            
            if (item.assignee != null || item.deadline != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    item.assignee?.takeIf { it.isNotBlank() }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFFF9800)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                    
                    item.deadline?.takeIf { it.isNotBlank() }?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingCard(status: ProcessingStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(status.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                "Please wait...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FailedCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(16.dp))
            Text("Processing Failed", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Try Again")
            }
        }
    }
}

@Composable
private fun WaitingCard(onProcess: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text("Ready to Process", fontWeight = FontWeight.SemiBold)
            Text(
                "This recording hasn't been processed yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onProcess) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Process Now")
            }
        }
    }
}

@Composable
private fun PersonalSummaryCard(
    recording: Recording,
    userEmail: String,
    onSend: (String, String) -> Unit,
    initialBody: String
) {
    var showPreview by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf(initialBody) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Me (Personal Summary)", fontWeight = FontWeight.SemiBold)
                    }
                    if (userEmail.isNotBlank()) {
                        Text(userEmail, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("No email set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                IconButton(onClick = { showPreview = !showPreview }) {
                    Icon(
                        if (showPreview) Icons.Default.ExpandLess else Icons.Default.Visibility,
                        contentDescription = if (showPreview) "Close Preview" else "Preview"
                    )
                }
            }
            
            if (showPreview) {
                Spacer(Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Email Content") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 15
                )
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = { 
                        onSend(userEmail, body)
                        showPreview = false // Auto-close after sending
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send Now")
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onSend(userEmail, body) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = userEmail.isNotBlank()
                ) {
                    Icon(Icons.Default.Email, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Send to Me")
                }
            }
        }
    }
}

@Composable
private fun StakeholderEmailCard(
    stakeholder: com.bigdictaphone.app.data.Stakeholder,
    recording: Recording,
    onSend: (String, String) -> Unit,
    initialBody: String
) {
    var showPreview by remember { mutableStateOf(true) } // Default open
    var body by remember { mutableStateOf(initialBody) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stakeholder.name, style = MaterialTheme.typography.titleMedium)
                    stakeholder.role?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                IconButton(onClick = { showPreview = !showPreview }) {
                    Icon(
                        if (showPreview) Icons.Default.ExpandLess else Icons.Default.Visibility,
                        contentDescription = "Toggle Preview"
                    )
                }
            }
            
            if (showPreview) {
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Draft Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                
                Spacer(Modifier.height(12.dp))
                
                Button(
                    onClick = { 
                        onSend(stakeholder.email, body)
                        showPreview = false // Auto-close
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Update")
                }
            }
        }
    }
}
