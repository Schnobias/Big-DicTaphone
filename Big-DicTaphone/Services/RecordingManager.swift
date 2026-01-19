import Foundation
import Combine

/// Central manager for all recordings - handles storage, processing, and state
@MainActor
class RecordingManager: ObservableObject {
    @Published var recordings: [Recording] = []
    @Published var stakeholders: [Stakeholder] = []
    @Published var isProcessing = false
    @Published var processingMessage = ""
    @Published var errorMessage: String?
    
    // Preferences
    @Published var userEmail: String {
        didSet { UserDefaults.standard.set(userEmail, forKey: "user_email") }
    }
    @Published var autoSendEnabled: Bool {
        didSet { UserDefaults.standard.set(autoSendEnabled, forKey: "auto_send_enabled") }
    }
    
    // SMTP Preferences
    @Published var smtpHost: String {
        didSet { UserDefaults.standard.set(smtpHost, forKey: "smtp_host") }
    }
    @Published var smtpPort: String {
        didSet { UserDefaults.standard.set(smtpPort, forKey: "smtp_port") }
    }
    @Published var smtpUser: String {
        didSet { UserDefaults.standard.set(smtpUser, forKey: "smtp_user") }
    }
    @Published var smtpPass: String {
        didSet { UserDefaults.standard.set(smtpPass, forKey: "smtp_pass") }
    }
    
    private let fileManager = FileManager.default
    private let recordingsKey = "saved_recordings"
    private let stakeholdersKey = "saved_stakeholders"
    
    // Services
    let audioRecorder = AudioRecorder()
    private let geminiService = GeminiService()
    
    init() {
        self.userEmail = UserDefaults.standard.string(forKey: "user_email") ?? ""
        self.autoSendEnabled = UserDefaults.standard.bool(forKey: "auto_send_enabled")
        self.smtpHost = UserDefaults.standard.string(forKey: "smtp_host") ?? "smtp.gmail.com"
        self.smtpPort = UserDefaults.standard.string(forKey: "smtp_port") ?? "587"
        self.smtpUser = UserDefaults.standard.string(forKey: "smtp_user") ?? ""
        self.smtpPass = UserDefaults.standard.string(forKey: "smtp_pass") ?? ""
        
        loadRecordings()
        loadStakeholders()
    }
    
    // MARK: - Stakeholder Management
    
    func addStakeholder(_ stakeholder: Stakeholder) {
        stakeholders.append(stakeholder)
        saveStakeholders()
    }
    
    func updateStakeholder(_ stakeholder: Stakeholder) {
        if let index = stakeholders.firstIndex(where: { $0.id == stakeholder.id }) {
            stakeholders[index] = stakeholder
            saveStakeholders()
        }
    }
    
    func deleteStakeholder(_ stakeholder: Stakeholder) {
        stakeholders.removeAll { $0.id == stakeholder.id }
        saveStakeholders()
    }
    
    private func saveStakeholders() {
        if let data = try? JSONEncoder().encode(stakeholders) {
            UserDefaults.standard.set(data, forKey: stakeholdersKey)
        }
    }
    
    private func loadStakeholders() {
        if let data = UserDefaults.standard.data(forKey: stakeholdersKey),
           let loaded = try? JSONDecoder().decode([Stakeholder].self, from: data) {
            stakeholders = loaded
        }
    }
    
    // MARK: - To-Do List
    
    var allActionItems: [ActionItem] {
        recordings.compactMap { $0.summary?.actionItems }.flatMap { $0 }
    }
    
    var incompleteActionItems: [ActionItem] {
        allActionItems.filter { !$0.completed }
    }
    
    func toggleActionItemCompletion(_ item: ActionItem, in recordingId: UUID?) {
        // Find recording containing this item
        // Note: We might need to look through all recordings if recordingId is nil or logic changes
        guard let id = recordingId ?? recordings.first(where: { rec in rec.summary?.actionItems.contains(where: { $0.id == item.id }) == true })?.id,
              let index = recordings.firstIndex(where: { $0.id == id }),
              var summary = recordings[index].summary,
              let itemIndex = summary.actionItems.firstIndex(where: { $0.id == item.id })
        else { return }
        
        // Update item in summary
        var updatedItems = summary.actionItems
        updatedItems[itemIndex].completed.toggle()
        
        // Update recording
        var updatedSummary = summary
        // Creating a new MeetingSummary with updated items (assuming simple properties)
        // Since MeetingSummary properties are 'let', we need to recreate it.
        // Wait, MeetingSummary in Swift is immutable structs.
        // We need a way to copy/update.
        // Actually, we can just instantiate a new one using the old values + new actionItems.
        updatedSummary = MeetingSummary(
            keyPoints: summary.keyPoints,
            actionItems: updatedItems,
            futurePoints: summary.futurePoints,
            managementDraft: summary.managementDraft,
            funnyQuote: summary.funnyQuote
        )
        
        var updatedRecording = recordings[index]
        updatedRecording.summary = updatedSummary
        updateRecording(updatedRecording)
    }

    // MARK: - Recording Management
    
    /// Add a new recording to the list
    func addRecording(_ recording: Recording) {
        recordings.insert(recording, at: 0)
        saveRecordings()
    }
    
    /// Update an existing recording
    func updateRecording(_ recording: Recording) {
        if let index = recordings.firstIndex(where: { $0.id == recording.id }) {
            recordings[index] = recording
            saveRecordings()
        }
    }
    
    /// Delete a recording
    func deleteRecording(_ recording: Recording) {
        // Delete audio file
        try? fileManager.removeItem(at: recording.audioURL)
        
        // Remove from list
        recordings.removeAll { $0.id == recording.id }
        saveRecordings()
    }
    
    /// Delete recordings at specific indices
    func deleteRecordings(at offsets: IndexSet) {
        for index in offsets {
            let recording = recordings[index]
            try? fileManager.removeItem(at: recording.audioURL)
        }
        recordings.remove(atOffsets: offsets)
        saveRecordings()
    }
    
    // MARK: - Processing Pipeline
    
    /// Process a recording: transcribe and summarize
    func processRecording(_ recording: Recording) async {
        var updatedRecording = recording
        
        isProcessing = true
        errorMessage = nil
        
        do {
            // Step 1: Transcribe
            processingMessage = "Transcribing audio..."
            updatedRecording.status = .transcribing
            updateRecording(updatedRecording)
            
            let speechRecognizer = SpeechRecognizer(language: recording.language)
            let authorized = await speechRecognizer.requestAuthorization()
            
            guard authorized else {
                throw ProcessingError.speechNotAuthorized
            }
            
            let transcription = try await speechRecognizer.transcribe(
                audioURL: recording.audioURL,
                language: recording.language
            )
            
            updatedRecording.transcription = transcription
            updateRecording(updatedRecording)
            
            // Step 2: Summarize with AI
            processingMessage = "Generating summary..."
            updatedRecording.status = .summarizing
            updateRecording(updatedRecording)
            
            if GeminiService.hasAPIKey {
                // Check Rate Limit
                let rateStatus = await RateLimitService.shared.canMakeRequest()
                switch rateStatus {
                case .dailyLimitReached:
                    throw GeminiError.apiError("Daily API limit reached (20/day). Try again tomorrow.")
                case .minuteLimitReached(let resetIn):
                    processingMessage = "Rate limited. Waiting \(Int(resetIn))s..."
                    try await Task.sleep(nanoseconds: UInt64(resetIn * 1_000_000_000))
                case .available:
                    break
                }
                
                // Record request
                await RateLimitService.shared.recordRequest()
                
                let summary = try await geminiService.generateSummary(
                    transcription: transcription,
                    language: recording.language
                )
                
                updatedRecording.summary = summary
                
                // Auto-fill recordingId in action items for tracking
                 if var summary = updatedRecording.summary {
                    let updatedItems = summary.actionItems.map { item -> ActionItem in
                        var newItem = item
                        newItem.recordingId = updatedRecording.id.uuidString
                        return newItem
                    }
                    updatedRecording.summary = MeetingSummary(
                        keyPoints: summary.keyPoints,
                        actionItems: updatedItems,
                        futurePoints: summary.futurePoints,
                        managementDraft: summary.managementDraft,
                        funnyQuote: summary.funnyQuote
                    )
                }

            } else {
                // No API key - create a basic summary
                updatedRecording.summary = MeetingSummary(
                    keyPoints: ["Transcription completed. Add a Gemini API key in Settings to enable AI summarization."],
                    actionItems: [],
                    futurePoints: [],
                    managementDraft: nil,
                    funnyQuote: nil
                )
            }
            
            updatedRecording.status = .complete
            updateRecording(updatedRecording)
            
            // Auto-Send Email Logic
            if autoSendEnabled && !userEmail.isEmpty, let summary = updatedRecording.summary {
                 // Check if SMTP is configured
                 if !smtpHost.isEmpty && !smtpUser.isEmpty && !smtpPass.isEmpty, let port = Int(smtpPort) {
                     Task {
                         print("Attempting to auto-send email via SMTP to \(userEmail)...")
                         let content = EmailComposer.personalSummaryEmail(for: updatedRecording)
                         do {
                             try await SMTPService.shared.sendEmail(
                                 host: smtpHost,
                                 port: port,
                                 user: smtpUser,
                                 pass: smtpPass,
                                 to: userEmail,
                                 subject: content.subject,
                                 body: content.body
                             )
                             print("✅ Auto-sent email via SMTP successfully.")
                         } catch {
                             print("❌ Failed to auto-send email via SMTP: \(error)")
                         }
                     }
                 } else {
                     print("⚠️ Auto-send enabled but SMTP not configured. Cannot send background email.")
                 }
            }
            
        } catch {
            updatedRecording.status = .failed
            updateRecording(updatedRecording)
            errorMessage = error.localizedDescription
            print("Processing error: \(error)")
        }
        
        isProcessing = false
        processingMessage = ""
    }
    
    /// Reprocess a recording (retry transcription and summarization)
    func reprocessRecording(_ recording: Recording) async {
        await processRecording(recording)
    }
    
    // MARK: - Persistence
    
    /// Save recordings to UserDefaults
    private func saveRecordings() {
        do {
            let data = try JSONEncoder().encode(recordings)
            UserDefaults.standard.set(data, forKey: recordingsKey)
        } catch {
            print("Failed to save recordings: \(error)")
        }
    }
    
    /// Load recordings from UserDefaults
    private func loadRecordings() {
        guard let data = UserDefaults.standard.data(forKey: recordingsKey) else {
            recordings = []
            return
        }
        
        do {
            recordings = try JSONDecoder().decode([Recording].self, from: data)
            
            // Verify audio files still exist
            recordings = recordings.filter { recording in
                fileManager.fileExists(atPath: recording.audioURL.path)
            }
        } catch {
            print("Failed to load recordings: \(error)")
            recordings = []
        }
    }
    
    // MARK: - Helpers
    
    /// Generate a unique filename for a new recording
    func generateFileName() -> String {
        let timestamp = ISO8601DateFormatter().string(from: Date())
            .replacingOccurrences(of: ":", with: "-")
        return "recording_\(timestamp).m4a"
    }
    
    /// Get total storage used by recordings
    var totalStorageUsed: String {
        let totalBytes = recordings.reduce(0) { total, recording in
            let size = (try? fileManager.attributesOfItem(atPath: recording.audioURL.path)[.size] as? Int) ?? 0
            return total + size
        }
        
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: Int64(totalBytes))
    }
}

/// Errors during recording processing
enum ProcessingError: LocalizedError {
    case speechNotAuthorized
    case transcriptionFailed
    case summarizationFailed
    
    var errorDescription: String? {
        switch self {
        case .speechNotAuthorized:
            return "Speech recognition is not authorized. Please enable it in Settings."
        case .transcriptionFailed:
            return "Failed to transcribe the recording."
        case .summarizationFailed:
            return "Failed to generate summary."
        }
    }
}
