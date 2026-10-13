import SwiftUI

/// Settings view for configuring the app
struct SettingsView: View {
    @EnvironmentObject var recordingManager: RecordingManager
    
    @AppStorage("user_email") private var userEmail = ""
    @AppStorage("default_language") private var defaultLanguage = RecordingLanguage.english.rawValue
    
    @State private var apiKey = ""
    @State private var showingAPIKey = false
    @State private var credentialError: String?
    @State private var showingClearDataAlert = false
    @State private var isAddingStakeholder = false
    @State private var editingStakeholder: Stakeholder?
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Toggle("Transcribe only on this iPhone", isOn: $recordingManager.localOnly)
                } header: {
                    Text("Transcription privacy")
                } footer: {
                    Text("On-device mode requires an installed Apple speech language and skips Gemini and auto-email. Auto uses the device language. Turn off to allow Apple speech servers and Gemini summaries. Android uses Whisper and supports automatic language detection independently.")
                }
                // Personal Settings
                Section {
                    TextField("Your Email", text: $recordingManager.userEmail)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                    
                    Toggle("Auto-send summary to me", isOn: $recordingManager.autoSendEnabled)
                    
                    Picker("Default Language", selection: $defaultLanguage) {
                        ForEach(RecordingLanguage.allCases) { language in
                            Text("\(language.flagEmoji) \(language.displayName)")
                                .tag(language.rawValue)
                        }
                    }
                } header: {
                    Text("Personal")
                } footer: {
                    Text("Your email is used as the default recipient for summary emails.")
                }
                
                // SMTP Settings
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Configure SMTP to enable automatic background sending.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                    
                    TextField("SMTP Host", text: $recordingManager.smtpHost)
                        .autocapitalization(.none)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                    
                    TextField("Port", text: $recordingManager.smtpPort)
                        .keyboardType(.numberPad)
                    
                    TextField("Username", text: $recordingManager.smtpUser)
                        .textContentType(.emailAddress)
                        .keyboardType(.emailAddress)
                        .autocapitalization(.none)
                    
                    SecureField("Password / App Password", text: $recordingManager.smtpPass)
                        .textContentType(.password)
                } header: {
                    Text("Email Sending Method (SMTP)")
                } footer: {
                    Text("For Gmail, use an App Password if 2FA is enabled.")
                }
                
                // Stakeholders
                Section {
                    ForEach(recordingManager.stakeholders) { stakeholder in
                        Button {
                            editingStakeholder = stakeholder
                        } label: {
                            VStack(alignment: .leading) {
                                Text(stakeholder.name)
                                    .font(.body)
                                Text(stakeholder.email)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                if let role = stakeholder.role, !role.isEmpty {
                                    Text(role)
                                        .font(.caption2)
                                        .foregroundStyle(.tertiary)
                                }
                            }
                            .foregroundStyle(.primary)
                        }
                    }
                    .onDelete { indexSet in
                        for index in indexSet {
                            recordingManager.deleteStakeholder(recordingManager.stakeholders[index])
                        }
                    }
                    
                    Button {
                        isAddingStakeholder = true
                    } label: {
                        Label("Add Stakeholder", systemImage: "plus")
                    }
                } header: {
                    Text("Stakeholders")
                } footer: {
                    Text("People who should receive management updates.")
                }
                
                // Gemini API Key
                Section {
                    HStack {
                        if showingAPIKey {
                            TextField("API Key", text: $apiKey)
                                .autocapitalization(.none)
                                .autocorrectionDisabled()
                        } else {
                            SecureField("API Key", text: $apiKey)
                        }
                        
                        Button {
                            showingAPIKey.toggle()
                        } label: {
                            Image(systemName: showingAPIKey ? "eye.slash" : "eye")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                    
                    Button {
                        if !GeminiService.saveAPIKey(apiKey) {
                            credentialError = "Could not save the API key securely."
                        } else {
                            credentialError = nil
                        }
                    } label: {
                        HStack {
                            Image(systemName: "checkmark.circle")
                            Text("Save API Key")
                        }
                    }
                    .disabled(apiKey.isEmpty)

                    if let credentialError {
                        Text(credentialError)
                            .foregroundStyle(.red)
                            .font(.footnote)
                    }
                    
                    if GeminiService.hasAPIKey {
                        HStack {
                            Image(systemName: "checkmark.seal.fill")
                                .foregroundStyle(.green)
                            Text("API Key Configured")
                                .foregroundStyle(.green)
                        }
                    }
                } header: {
                    Text("AI Summarization")
                } footer: {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Get a free API key from Google AI Studio to enable AI-powered summaries.")
                        
                        Link(destination: URL(string: "https://makersuite.google.com/app/apikey")!) {
                            HStack {
                                Image(systemName: "arrow.up.right.square")
                                Text("Get API Key")
                            }
                            .font(.footnote)
                        }
                    }
                }
                
                // Storage Info
                Section {
                    LabeledContent("Recordings") {
                        Text("\(recordingManager.recordings.count)")
                    }
                    
                    LabeledContent("Storage Used") {
                        Text(recordingManager.totalStorageUsed)
                    }
                } header: {
                    Text("Storage")
                }
                
                // Danger Zone
                Section {
                    Button(role: .destructive) {
                        showingClearDataAlert = true
                    } label: {
                        HStack {
                            Image(systemName: "trash")
                            Text("Delete All Recordings")
                        }
                    }
                    
                    Button(role: .destructive) {
                        GeminiService.clearAPIKey()
                        apiKey = ""
                    } label: {
                        HStack {
                            Image(systemName: "key.slash")
                            Text("Remove API Key")
                        }
                    }
                } header: {
                    Text("Danger Zone")
                }
                
                // About
                Section {
                    LabeledContent("Version") {
                        Text("1.0.0")
                    }
                    
                    Link(destination: URL(string: "https://github.com")!) {
                        HStack {
                            Text("Source Code")
                            Spacer()
                            Image(systemName: "arrow.up.right.square")
                                .foregroundStyle(.secondary)
                        }
                    }
                } header: {
                    Text("About")
                } footer: {
                    VStack(spacing: 4) {
                        Text("Big DicTa")
                            .font(.headline)
                        Text("Voice notes & meeting summaries powered by AI")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $isAddingStakeholder) {
                StakeholderEditView()
            }
            .sheet(item: $editingStakeholder) { stakeholder in
                StakeholderEditView(stakeholder: stakeholder)
            }
            .onAppear {
                // Load existing API key (masked)
                if GeminiService.hasAPIKey {
                    apiKey = "••••••••••••••••"
                }
            }
            .alert("Delete All Recordings?", isPresented: $showingClearDataAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Delete All", role: .destructive) {
                    deleteAllRecordings()
                }
            } message: {
                Text("This will permanently delete all your recordings and cannot be undone.")
            }
        }
    }
    
    private func deleteAllRecordings() {
        // Delete all audio files
        for recording in recordingManager.recordings {
            try? FileManager.default.removeItem(at: recording.audioURL)
        }
        
        // Clear the recordings list
        recordingManager.recordings.removeAll()
    }
}

struct StakeholderEditView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var recordingManager: RecordingManager
    
    var stakeholder: Stakeholder?
    @State private var name = ""
    @State private var email = ""
    @State private var role = ""
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                TextField("Email", text: $email)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                TextField("Role (Optional)", text: $role)
            }
            .navigationTitle(stakeholder == nil ? "Add Stakeholder" : "Edit Stakeholder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if let existing = stakeholder {
                            var updated = existing
                            updated.name = name
                            updated.email = email
                            updated.role = role.isEmpty ? nil : role
                            recordingManager.updateStakeholder(updated)
                        } else {
                            let new = Stakeholder(name: name, email: email, role: role.isEmpty ? nil : role)
                            recordingManager.addStakeholder(new)
                        }
                        dismiss()
                    }
                    .disabled(name.isEmpty || email.isEmpty)
                }
            }
            .onAppear {
                if let s = stakeholder {
                    name = s.name
                    email = s.email
                    role = s.role ?? ""
                }
            }
        }
    }
}

#Preview {
    SettingsView()
        .environmentObject(RecordingManager())
}
