package com.bigdictaphone.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bigdictaphone.app.data.RecordingLanguage
import com.bigdictaphone.app.services.AudioRecorderService
import com.bigdictaphone.app.viewmodel.RecordingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(viewModel: RecordingViewModel) {
    val context = LocalContext.current
    
    val isRecording by viewModel.audioRecorder.isRecording.collectAsState()
    val isPaused by viewModel.audioRecorder.isPaused.collectAsState()
    val recordingTime by viewModel.audioRecorder.recordingTime.collectAsState()
    val audioLevel by viewModel.audioRecorder.audioLevel.collectAsState()
    val audioLevelLeft by viewModel.audioRecorder.audioLevelLeft.collectAsState()
    val audioLevelRight by viewModel.audioRecorder.audioLevelRight.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    
    var hasPermission by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var recordingTitle by remember { mutableStateOf("") }
    var stoppedRecording by remember { mutableStateOf<com.bigdictaphone.app.data.Recording?>(null) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    // Check permission on launch
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Record") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Timer display
            TimerDisplay(
                time = recordingTime,
                isRecording = isRecording
            )
            
            // Stereo audio level visualization (L/R channels)
            StereoAudioLevelVisualization(
                levelLeft = audioLevelLeft,
                levelRight = audioLevelRight,
                isRecording = isRecording && !isPaused
            )
            
            // Language selector
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { viewModel.setLanguage(it) },
                enabled = !isRecording
            )
            
            // Record button
            RecordButton(
                isRecording = isRecording,
                isPaused = isPaused,
                audioLevel = audioLevel,
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        return@RecordButton
                    }
                    
                    if (isRecording) {
                        stoppedRecording = viewModel.stopRecording()
                        recordingTitle = ""
                        showSaveDialog = true
                    } else {
                        viewModel.startRecording()
                    }
                }
            )
            
            // Control buttons (when recording)
            if (isRecording) {
                ControlButtons(
                    isPaused = isPaused,
                    onPauseResume = {
                        if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                    },
                    onCancel = {
                        viewModel.cancelRecording()
                    }
                )
            }
        }
    }
    
    // Error snackbar
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Auto-clear after showing
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
    
    // Save dialog
    if (showSaveDialog) {
        SaveRecordingDialog(
            recording = stoppedRecording,
            title = recordingTitle,
            onTitleChange = { recordingTitle = it },
            onSave = {
                viewModel.saveRecording(recordingTitle.takeIf { it.isNotBlank() })
                showSaveDialog = false
                stoppedRecording = null
            },
            onDiscard = {
                viewModel.cancelRecording()
                showSaveDialog = false
                stoppedRecording = null
            }
        )
    }
}

@Composable
private fun TimerDisplay(time: Long, isRecording: Boolean) {
    Text(
        text = AudioRecorderService.formatTime(time),
        fontSize = 64.sp,
        fontWeight = FontWeight.Thin,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = if (isRecording) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}

@Composable
private fun AudioLevelVisualization(level: Float, isRecording: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(20) { index ->
            val threshold = index / 20f
            val isActive = isRecording && level > threshold
            
            val height by animateFloatAsState(
                targetValue = if (isActive) {
                    (10f + (50f * (level - threshold) / (1f - threshold)) * (0.8f + kotlin.random.Random.nextFloat() * 0.4f))
                } else {
                    10f
                },
                animationSpec = tween(100),
                label = "barHeight"
            )
            
            val color by animateColorAsState(
                targetValue = when {
                    !isActive -> MaterialTheme.colorScheme.surfaceVariant
                    index < 14 -> Color(0xFF4CAF50) // Green
                    index < 17 -> Color(0xFFFFC107) // Yellow
                    else -> Color(0xFFF44336) // Red
                },
                label = "barColor"
            )
            
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun StereoAudioLevelVisualization(
    levelLeft: Float,
    levelRight: Float,
    isRecording: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "L",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(16.dp)
            )
            AudioLevelBar(level = levelLeft, isRecording = isRecording)
        }
        
        // Right channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "R",
                style = MaterialTheme.typography.labelMedium,
                color = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(16.dp)
            )
            AudioLevelBar(level = levelRight, isRecording = isRecording)
        }
    }
}

@Composable
private fun AudioLevelBar(level: Float, isRecording: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(30) { index ->
            val threshold = index / 30f
            val isActive = isRecording && level > threshold
            
            val color by animateColorAsState(
                targetValue = when {
                    !isActive -> MaterialTheme.colorScheme.surfaceVariant
                    index < 21 -> Color(0xFF4CAF50) // Green (70%)
                    index < 26 -> Color(0xFFFFC107) // Yellow (17%)
                    else -> Color(0xFFF44336) // Red (13%)
                },
                label = "barColor"
            )
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (isActive) 20.dp else 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    selectedLanguage: RecordingLanguage,
    onLanguageSelected: (RecordingLanguage) -> Unit,
    enabled: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecordingLanguage.entries.forEach { language ->
            val isSelected = language == selectedLanguage
            
            FilledTonalButton(
                onClick = { onLanguageSelected(language) },
                enabled = enabled,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("${language.flag} ${language.displayName}")
            }
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    isPaused: Boolean,
    audioLevel: Float,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    
    val scale = if (isRecording && !isPaused) {
        1f + audioLevel * 0.2f
    } else {
        1f
    }
    
    Box(contentAlignment = Alignment.Center) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
        )
        
        // Pulsing ring when recording
        if (isRecording && !isPaused) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale * scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            )
        }
        
        // Main button
        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.size(96.dp),
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ) {
            if (isRecording) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlButtons(
    isPaused: Boolean,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        // Cancel button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(
                onClick = onCancel,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
            Text("Cancel", style = MaterialTheme.typography.labelSmall)
        }
        
        // Pause/Resume button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(
                onClick = onPauseResume,
                modifier = Modifier.size(56.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause"
                )
            }
            Text(if (isPaused) "Resume" else "Pause", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SaveRecordingDialog(
    recording: com.bigdictaphone.app.data.Recording?,
    title: String,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Don't dismiss on outside click */ },
        title = { Text("Save Recording") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                recording?.let {
                    Text(
                        "Duration: ${it.formattedDuration}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Language: ${it.language.flag} ${it.language.displayName}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Text(
                    "The recording will be transcribed and summarized automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save & Process")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
