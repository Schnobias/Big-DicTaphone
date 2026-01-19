package com.bigdictaphone.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bigdictaphone.app.data.RecordingLanguage
import com.bigdictaphone.app.data.Stakeholder
import com.bigdictaphone.app.viewmodel.RecordingViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RecordingViewModel = viewModel()
) {
    val recordings by viewModel.recordings.collectAsState()
    val storedApiKey by viewModel.geminiApiKey.collectAsState(initial = "")
    val storedEmail by viewModel.userEmail.collectAsState(initial = "")
    val hasApiKey by viewModel.hasApiKey.collectAsState(initial = false)
    val autoSendEnabled by viewModel.autoSendEnabled.collectAsState(initial = false)
    val stakeholders by viewModel.stakeholders.collectAsState(initial = emptyList())
    val defaultLanguage by viewModel.defaultLanguage.collectAsState(initial = "AUTO")
    
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var userEmail by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var showAddStakeholderDialog by remember { mutableStateOf(false) }
    var editingStakeholder by remember { mutableStateOf<Stakeholder?>(null) }
    
    // Initialize from stored values
    LaunchedEffect(storedEmail) {
        if (userEmail.isEmpty()) userEmail = storedEmail
    }
    
    LaunchedEffect(storedApiKey) {
        if (apiKey.isEmpty() && storedApiKey.isNotEmpty()) {
            apiKey = "••••••••••••••••"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Personal Settings
            SettingsSection(title = "Personal") {
                // Email
                OutlinedTextField(
                    value = userEmail,
                    onValueChange = { 
                        userEmail = it
                        viewModel.saveUserEmail(it)
                    },
                    label = { Text("Your Email") },
                    placeholder = { Text("email@example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(12.dp))
                
                // Auto-send toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-send summary to me")
                        Text(
                            "Email summary automatically after processing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSendEnabled,
                        onCheckedChange = { viewModel.setAutoSendEnabled(it) }
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // SMTP Settings
            SettingsSection(title = "Email Sending Method (SMTP)") {
                val smtpHost by viewModel.smtpHost.collectAsState(initial = "")
                val smtpPort by viewModel.smtpPort.collectAsState(initial = "587")
                val smtpUser by viewModel.smtpUser.collectAsState(initial = "")
                val smtpPass by viewModel.smtpPass.collectAsState(initial = "")
                
                var host by remember { mutableStateOf("") }
                var port by remember { mutableStateOf("") }
                var user by remember { mutableStateOf("") }
                var pass by remember { mutableStateOf("") }
                var showPass by remember { mutableStateOf(false) }
                
                LaunchedEffect(smtpHost, smtpPort, smtpUser, smtpPass) {
                    // Pre-fill defaults for Gmail if empty
                    host = if (smtpHost.isNotEmpty()) smtpHost else "smtp.gmail.com"
                    port = if (smtpPort.isNotEmpty()) smtpPort else "587"
                    
                    if (user.isEmpty()) user = smtpUser
                    if (pass.isEmpty()) pass = smtpPass
                }
                
                Text(
                    "Configure SMTP to send emails automatically in the background without opening your email app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("SMTP Host") },
                        placeholder = { Text("smtp.gmail.com") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        placeholder = { Text("587") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("SMTP User/Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("SMTP Password / App Password") },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(Modifier.height(4.dp))
                Text(
                    "Note: For Gmail, use an App Password if 2FA is enabled.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = { viewModel.saveSmtpSettings(host, port, user, pass) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = host.isNotBlank() && port.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save SMTP Settings")
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Language Settings
            SettingsSection(title = "Language") {
                val languages = RecordingLanguage.values()
                var expanded by remember { mutableStateOf(false) }
                val selectedLanguage = languages.find { it.locale == defaultLanguage || it.name == defaultLanguage } 
                    ?: RecordingLanguage.AUTO
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = "${selectedLanguage.flag} ${selectedLanguage.displayName}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Default Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text("${lang.flag} ${lang.displayName}") },
                                onClick = {
                                    viewModel.saveDefaultLanguage(lang.name)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                Text(
                    "Auto-detect uses AI to identify the spoken language",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Stakeholders
            SettingsSection(title = "Stakeholders") {
                Text(
                    "Add people who should receive management updates",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(12.dp))
                
                stakeholders.forEach { stakeholder ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stakeholder.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stakeholder.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                stakeholder.role?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Row {
                                IconButton(onClick = { editingStakeholder = stakeholder }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { viewModel.removeStakeholder(stakeholder) }) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { showAddStakeholderDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Stakeholder")
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // API Key Settings
            SettingsSection(title = "AI Summarization") {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Gemini API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                Button(
                    onClick = { viewModel.saveApiKey(apiKey) },
                    enabled = apiKey.isNotBlank() && !apiKey.startsWith("••"),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save API Key")
                }
                
                if (hasApiKey) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("API Key Configured", color = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Get a free API key from Google AI Studio", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "aistudio.google.com/app/apikey",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Storage Info
            SettingsSection(title = "Storage") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recordings")
                    Text("${recordings.size}")
                }
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Storage Used")
                    Text(viewModel.getTotalStorageUsed())
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // Danger Zone
            SettingsSection(title = "Danger Zone") {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete All Recordings")
                }
                
                Spacer(Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { showClearKeyDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.KeyOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove API Key")
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 16.dp))
            
            // About
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version")
                    Text("1.1.0")
                }
            }
            
            // Footer
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🍆🎤 Big-DicTaphone", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Voice notes & meeting summaries powered by AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    
    // Delete all dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete All Recordings?") },
            text = { Text("This will permanently delete all your recordings and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAllRecordings()
                    showDeleteDialog = false
                }) {
                    Text("Delete All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // Clear API key dialog
    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text("Remove API Key?") },
            text = { Text("This will remove your saved API key.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearApiKey()
                    apiKey = ""
                    showClearKeyDialog = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearKeyDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    // Add/Edit stakeholder dialog
    if (showAddStakeholderDialog || editingStakeholder != null) {
        val isEditing = editingStakeholder != null
        var name by remember { mutableStateOf(editingStakeholder?.name ?: "") }
        var email by remember { mutableStateOf(editingStakeholder?.email ?: "") }
        var role by remember { mutableStateOf(editingStakeholder?.role ?: "") }
        
        AlertDialog(
            onDismissRequest = { 
                showAddStakeholderDialog = false 
                editingStakeholder = null
            },
            title = { Text(if (isEditing) "Edit Stakeholder" else "Add Stakeholder") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = role,
                        onValueChange = { role = it },
                        label = { Text("Role (optional)") },
                        placeholder = { Text("e.g., Manager, Team Lead") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stakeholder = Stakeholder(
                            id = editingStakeholder?.id ?: UUID.randomUUID().toString(),
                            name = name,
                            email = email,
                            role = role.takeIf { it.isNotBlank() }
                        )
                        if (isEditing) {
                            viewModel.updateStakeholder(stakeholder)
                        } else {
                            viewModel.addStakeholder(stakeholder)
                        }
                        showAddStakeholderDialog = false
                        editingStakeholder = null
                    },
                    enabled = name.isNotBlank() && email.isNotBlank()
                ) {
                    Text(if (isEditing) "Save" else "Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddStakeholderDialog = false 
                    editingStakeholder = null
                }) { 
                    Text("Cancel") 
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
