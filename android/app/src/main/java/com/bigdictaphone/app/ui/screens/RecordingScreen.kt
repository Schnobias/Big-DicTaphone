package com.bigdictaphone.app.ui.screens

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import com.bigdictaphone.app.data.TranscriptionMode
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
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    
    val mode by viewModel.transcriptionMode.collectAsState(initial = TranscriptionMode.LOCAL)
    val pending by viewModel.currentRecording.collectAsState()
    val busy by viewModel.captureBusy.collectAsState()
    var hasPermission by remember { mutableStateOf(false) }
    var recordingTitle by rememberSaveable { mutableStateOf("") }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionMessage = if (!hasPermission) "Microphone access is needed to record. You can enable it in Android app settings."
        else if (Build.VERSION.SDK_INT >= 33 && results[Manifest.permission.POST_NOTIFICATIONS] == false)
            "Recording can continue in the background. Enable notifications in Android settings to use lock-screen controls."
        else "Ready. Tap the microphone to start recording."
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(if (mode == TranscriptionMode.LOCAL) "On-device transcription · no upload" else "Gemini cloud · uploads audio on save")
            permissionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            // Timer display
            TimerDisplay(
                time = recordingTime,
                isRecording = isRecording
            )
            
            AudioLevelBar(level = audioLevel, isRecording = isRecording && !isPaused)
            Text("Microphone level", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(16.dp))
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
                    if (busy) return@RecordButton
                    hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (!hasPermission) {
                        permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.RECORD_AUDIO))
                        return@RecordButton
                    }
                    
                    if (isRecording) {
                        viewModel.stopRecording()
                        recordingTitle = ""
                    } else {
                        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            permissionMessage = "Enable notifications in Android settings for lock-screen pause, resume and save controls."
                        } else permissionMessage = null
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
    
    // Save dialog
    if (pending != null && !isRecording) {
        SaveRecordingDialog(
            recording = pending,
            title = recordingTitle,
            busy = busy,
            onTitleChange = { recordingTitle = it },
            onSave = {
                viewModel.saveRecording(recordingTitle.takeIf { it.isNotBlank() })
            },
            onDiscard = {
                viewModel.cancelRecording()
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
@OptIn(ExperimentalLayoutApi::class)
private fun LanguageSelector(
    selectedLanguage: RecordingLanguage,
    onLanguageSelected: (RecordingLanguage) -> Unit,
    enabled: Boolean
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 2,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RecordingLanguage.entries.forEach { language ->
            val isSelected = language == selectedLanguage
            
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                onClick = { onLanguageSelected(language) },
                enabled = enabled,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("${language.flag} ${language.displayName}", maxLines = 1, style = MaterialTheme.typography.labelMedium)
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
    busy: Boolean,
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
                    if (recording?.transcriptionMode == TranscriptionMode.CLOUD) "Audio will be uploaded to Gemini for transcription and a summary." else "Audio stays on this phone. A downloaded local model is needed to transcribe it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !busy) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save & Process")
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, enabled = !busy) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
