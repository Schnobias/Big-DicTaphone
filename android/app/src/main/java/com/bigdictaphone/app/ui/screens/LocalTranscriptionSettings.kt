package com.bigdictaphone.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigdictaphone.app.data.TranscriptionMode
import com.bigdictaphone.app.services.LocalModel
import com.bigdictaphone.app.viewmodel.RecordingViewModel

@Composable
fun LocalTranscriptionSettings(viewModel: RecordingViewModel) {
    val mode by viewModel.transcriptionMode.collectAsState(initial = TranscriptionMode.LOCAL)
    val model by viewModel.localModel.collectAsState(initial = LocalModel.BASE)
    val status by viewModel.modelStatus.collectAsState()
    val readiness by viewModel.modelStore.readiness.collectAsState()
    val downloading by viewModel.isDownloading.collectAsState()
    val processing by viewModel.isProcessing.collectAsState()
    LaunchedEffect(model) { viewModel.modelStore.refresh(model) }
    var confirmCloud by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Transcription", style = MaterialTheme.typography.titleLarge)
        Text("On this phone keeps audio and transcripts here. Download a model once, then transcribe in airplane mode. No API key needed.")
        TranscriptionMode.values().forEach { option ->
            Row {
                RadioButton(selected = mode == option, enabled = !processing,
                    onClick = { if (option == TranscriptionMode.CLOUD) confirmCloud = true else viewModel.setTranscriptionMode(option) })
                Text(option.title, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (mode == TranscriptionMode.CLOUD) Text("New recordings will be uploaded to Google Gemini when saved. Existing recordings keep their original mode.", color = MaterialTheme.colorScheme.error)
        Text("Offline model", style = MaterialTheme.typography.titleMedium)
        LocalModel.values().forEach { option ->
            Row {
                RadioButton(selected = model == option, enabled = !processing && !downloading, onClick = { viewModel.setLocalModel(option) })
                Text(option.title, modifier = Modifier.padding(top = 12.dp))
            }
        }
        // Reading status also recomposes after a completed download.
        Text(when (readiness[model]) {
            com.bigdictaphone.app.services.ModelReadiness.Ready -> "Selected model is verified and ready."
            com.bigdictaphone.app.services.ModelReadiness.Verifying -> "Verifying selected model…"
            com.bigdictaphone.app.services.ModelReadiness.Downloading -> "Downloading selected model…"
            com.bigdictaphone.app.services.ModelReadiness.Damaged -> "Selected model is damaged. Download it again."
            com.bigdictaphone.app.services.ModelReadiness.Error -> "Model is unavailable. Try downloading it again."
            else -> "Selected model needs downloading. Recordings can still be saved."
        })
        Text("Both models support Dutch, English and automatic language detection. Base is the default for Pixel 11 Pro. Local transcription does not generate summaries or identify speakers.", style = MaterialTheme.typography.bodySmall)
        if (status.isNotBlank()) Text(status)
        if (downloading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = viewModel::cancelModelDownload) { Text("Cancel download") }
        } else {
            Button(onClick = { viewModel.downloadModel(model) }, enabled = !processing) {
                Text(if (readiness[model] == com.bigdictaphone.app.services.ModelReadiness.Ready) "Download model again" else "Download model")
            }
        }
        Text("Downloads use your internet connection (Hugging Face). Transcription runs on the CPU and continues with the screen locked or while you use another app.", style = MaterialTheme.typography.bodySmall)
    }
    if (confirmCloud) AlertDialog(
        onDismissRequest = { confirmCloud = false },
        title = { Text("Use Gemini cloud?") },
        text = { Text("Audio from new recordings will be sent to Google when you save it, for transcription and summaries. Auto-email also applies if enabled. Local recordings stay local.") },
        confirmButton = { TextButton(onClick = { viewModel.setTranscriptionMode(TranscriptionMode.CLOUD); confirmCloud = false }) { Text("Use cloud") } },
        dismissButton = { TextButton(onClick = { confirmCloud = false }) { Text("Cancel") } }
    )
}
