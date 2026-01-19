import SwiftUI
import AVFoundation

/// Detailed view for a single recording with transcription, summary, and email options
struct RecordingDetailView: View {
    @Binding var recording: Recording
    @EnvironmentObject var recordingManager: RecordingManager
    
    @State private var isPlaying = false
    @State private var audioPlayer: AVAudioPlayer?
    @State private var playbackProgress: Double = 0
    @State private var playbackTimer: Timer?
    
    @State private var showingPersonalEmail = false
    @State private var showingManagementEmail = false
    @State private var showingTranscription = false
    @State private var showingReprocessAlert = false
    @State private var userEmail = UserDefaults.standard.string(forKey: "user_email") ?? ""
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                // Header with audio player
                headerSection
                
                // Processing status or summary content
                if recording.status.isProcessing {
                    processingSection
                } else if recording.status == .failed {
                    failedSection
                } else if recording.status == .complete {
                    summaryContent
                } else {
                    waitingSection
                }
            }
            .padding()
        }
        .navigationTitle(recording.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    if recording.status == .complete {
                        Button {
                            showingPersonalEmail = true
                        } label: {
                            Label("Email Summary to Me", systemImage: "envelope")
                        }
                        
                        Button {
                            showingManagementEmail = true
                        } label: {
                            Label("Email Management Update", systemImage: "person.2")
                        }
                        
                        Divider()
                    }
                    
                    Button {
                        showingReprocessAlert = true
                    } label: {
                        Label("Reprocess", systemImage: "arrow.clockwise")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $showingPersonalEmail) {
            if MailComposeView.canSendMail {
                MailComposeView(
                    emailContent: EmailComposer.personalSummaryEmail(for: recording),
                    recipients: userEmail.isEmpty ? [] : [userEmail],
                    onDismiss: { showingPersonalEmail = false }
                )
            } else {
                noMailAlert
            }
        }
        .sheet(isPresented: $showingManagementEmail) {
            if MailComposeView.canSendMail {
                MailComposeView(
                    emailContent: EmailComposer.managementUpdateEmail(for: recording),
                    recipients: [],
                    onDismiss: { showingManagementEmail = false }
                )
            } else {
                noMailAlert
            }
        }
        .alert("Reprocess Recording?", isPresented: $showingReprocessAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Reprocess") {
                Task {
                    await recordingManager.reprocessRecording(recording)
                }
            }
        } message: {
            Text("This will transcribe and summarize the recording again.")
        }
        .onDisappear {
            stopPlayback()
        }
    }
    
    // MARK: - Header Section
    
    private var headerSection: some View {
        VStack(spacing: 16) {
            // Recording info
            HStack {
                Label(recording.language.displayName, systemImage: "globe")
                Spacer()
                Label(formattedDate, systemImage: "calendar")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            
            // Audio player
            VStack(spacing: 12) {
                // Progress bar
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color(.systemGray5))
                            .frame(height: 8)
                        
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color.accentColor)
                            .frame(width: geometry.size.width * playbackProgress, height: 8)
                    }
                }
                .frame(height: 8)
                
                // Time labels
                HStack {
                    Text(formatTime(playbackProgress * recording.duration))
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    
                    Spacer()
                    
                    Text(recording.formattedDuration)
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                
                // Play button
                Button {
                    togglePlayback()
                } label: {
                    HStack {
                        Image(systemName: isPlaying ? "pause.fill" : "play.fill")
                        Text(isPlaying ? "Pause" : "Play Recording")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding()
            .background(Color(.systemGray6).opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }
    
    // MARK: - Summary Content
    
    private var summaryContent: some View {
        VStack(spacing: 20) {
            // Key Points
            if let summary = recording.summary, !summary.keyPoints.isEmpty {
                SummarySection(title: "Key Points", icon: "lightbulb.fill", color: .yellow) {
                    ForEach(summary.keyPoints, id: \.self) { point in
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "circle.fill")
                                .font(.system(size: 6))
                                .foregroundStyle(.secondary)
                                .padding(.top, 6)
                            
                            Text(point)
                                .font(.body)
                        }
                    }
                }
            }
            
            // Action Items
            if let summary = recording.summary, !summary.actionItems.isEmpty {
                SummarySection(title: "Action Items", icon: "checkmark.circle.fill", color: .green) {
                    ForEach(summary.actionItems) { item in
                        ActionItemRow(item: item)
                    }
                }
            }
            
            // Future Points
            if let summary = recording.summary, !summary.futurePoints.isEmpty {
                SummarySection(title: "Future Discussion", icon: "calendar.badge.clock", color: .blue) {
                    ForEach(summary.futurePoints, id: \.self) { point in
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "arrow.right.circle")
                                .foregroundStyle(.blue)
                            
                            Text(point)
                                .font(.body)
                        }
                    }
                }
            }
            
            // Transcription (collapsible)
            if let transcription = recording.transcription, !transcription.isEmpty {
                DisclosureGroup(isExpanded: $showingTranscription) {
                    Text(transcription)
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                        .padding(.top, 12)
                } label: {
                    Label("Full Transcription", systemImage: "doc.text")
                        .font(.headline)
                }
                .padding()
                .background(Color(.systemGray6).opacity(0.5))
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }
            
            // Email Cards
            emailSection
        }
    }
    
    // MARK: - Email Cards Section
    
    private var emailSection: some View {
        VStack(spacing: 16) {
            // Personal Summary Card
            PersonalSummaryCard(recording: recording, userEmail: userEmail)
            
            // Stakeholder Cards
            if !recordingManager.stakeholders.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Stakeholder Updates")
                        .font(.headline)
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 4)
                        .padding(.top, 8)
                    
                    ForEach(recordingManager.stakeholders) { stakeholder in
                        StakeholderEmailCard(stakeholder: stakeholder, recording: recording)
                    }
                }
            }
        }
        .padding(.top, 8)
    }
    
    // MARK: - Status Sections
    
    private var processingSection: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
            
            Text(recording.status.rawValue)
                .font(.headline)
            
            Text("Please wait while we process your recording...")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private var failedSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundStyle(.red)
            
            Text("Processing Failed")
                .font(.headline)
            
            if let error = recordingManager.errorMessage {
                Text(error)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            
            Button {
                Task {
                    await recordingManager.reprocessRecording(recording)
                }
            } label: {
                Label("Try Again", systemImage: "arrow.clockwise")
                    .padding()
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private var waitingSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "waveform")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            
            Text("Ready to Process")
                .font(.headline)
            
            Text("This recording hasn't been processed yet.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            
            Button {
                Task {
                    await recordingManager.processRecording(recording)
                }
            } label: {
                Label("Process Now", systemImage: "sparkles")
                    .padding()
                    .background(Color.accentColor)
                    .foregroundStyle(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(40)
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
    
    private var noMailAlert: some View {
        VStack(spacing: 16) {
            Image(systemName: "envelope.badge.shield.half.filled")
                .font(.system(size: 48))
                .foregroundStyle(.orange)
            
            Text("Mail Not Available")
                .font(.headline)
            
            Text("Please configure a mail account in Settings to send emails.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Close") {
                showingPersonalEmail = false
                showingManagementEmail = false
            }
            .buttonStyle(.borderedProminent)
        }
        .padding(40)
    }
    
    // MARK: - Playback
    
    private func togglePlayback() {
        if isPlaying {
            stopPlayback()
        } else {
            startPlayback()
        }
    }
    
    private func startPlayback() {
        do {
            audioPlayer = try AVAudioPlayer(contentsOf: recording.audioURL)
            audioPlayer?.prepareToPlay()
            audioPlayer?.play()
            isPlaying = true
            
            playbackTimer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
                if let player = audioPlayer {
                    playbackProgress = player.currentTime / player.duration
                    
                    if !player.isPlaying {
                        stopPlayback()
                    }
                }
            }
        } catch {
            print("Failed to play audio: \(error)")
        }
    }
    
    private func stopPlayback() {
        audioPlayer?.stop()
        audioPlayer = nil
        playbackTimer?.invalidate()
        playbackTimer = nil
        isPlaying = false
    }
    
    // MARK: - Helpers
    
    private var formattedDate: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: recording.date)
    }
    
    private func formatTime(_ time: TimeInterval) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}

// MARK: - Supporting Views

/// A styled section in the summary
struct SummarySection<Content: View>: View {
    let title: String
    let icon: String
    let color: Color
    @ViewBuilder let content: Content
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(color)
                Text(title)
                    .font(.headline)
            }
            
            VStack(alignment: .leading, spacing: 12) {
                content
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.systemGray6).opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// A row displaying an action item
struct ActionItemRow: View {
    let item: ActionItem
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Image(systemName: item.completed ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(item.completed ? .green : .secondary)
                
                Text(item.task)
                    .font(.body)
                    .strikethrough(item.completed)
            }
            
            HStack(spacing: 16) {
                if let assignee = item.assignee, !assignee.isEmpty {
                    Label(assignee, systemImage: "person.fill")
                        .font(.caption)
                        .foregroundStyle(.orange)
                }
                
                if let deadline = item.deadline, !deadline.isEmpty {
                    Label(deadline, systemImage: "calendar")
                        .font(.caption)
                        .foregroundStyle(.green)
                }
            }
            .padding(.leading, 24)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct PersonalSummaryCard: View {
    let recording: Recording
    let userEmail: String
    
    @State private var isExpanded = false
    @State private var draftBody: String = ""
    @State private var showingMail = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Personal Summary", systemImage: "person.crop.circle")
                    .font(.headline)
                
                Spacer()
                
                Button {
                    withAnimation {
                        isExpanded.toggle()
                        if isExpanded && draftBody.isEmpty {
                            draftBody = EmailComposer.personalSummaryEmail(for: recording).body
                        }
                    }
                } label: {
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                }
            }
            
            if isExpanded {
                Divider()
                
                Text("Draft Content:")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                
                TextEditor(text: $draftBody)
                    .frame(minHeight: 100)
                    .padding(4)
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                
                Button {
                    showingMail = true
                } label: {
                    Label("Send to Me", systemImage: "paperplane.fill")
                        .frame(maxWidth: .infinity)
                        .padding(10)
                        .background(Color.accentColor)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .disabled(userEmail.isEmpty)
                
                if userEmail.isEmpty {
                    Text("Configure email in Settings")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            } else {
                 Button {
                     draftBody = EmailComposer.personalSummaryEmail(for: recording).body
                     showingMail = true
                 } label: {
                     Label("Send to Me", systemImage: "envelope")
                        .font(.subheadline)
                 }
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .sheet(isPresented: $showingMail) {
            if MailComposeView.canSendMail {
                MailComposeView(
                    emailContent: EmailContent(subject: "Meeting Summary: \(recording.title)", body: draftBody, isHTML: false),
                    recipients: [userEmail],
                    onDismiss: { showingMail = false; isExpanded = false }
                )
            } else {
                Text("Mail not configured") // Simplified for brevity
            }
        }
    }
}

struct StakeholderEmailCard: View {
    let stakeholder: Stakeholder
    let recording: Recording
    
    @State private var isExpanded = true // Open by default
    @State private var draftBody: String = ""
    @State private var showingMail = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                VStack(alignment: .leading) {
                    Text(stakeholder.name)
                        .font(.headline)
                    if let role = stakeholder.role {
                        Text(role)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                
                Spacer()
                
                Button {
                    withAnimation { isExpanded.toggle() }
                } label: {
                    Image(systemName: isExpanded ? "chevron.up" : "eye")
                        .foregroundStyle(.secondary)
                }
            }
            
            if isExpanded {
                Divider()
                
                Text("Draft Update:")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                
                TextEditor(text: $draftBody)
                    .frame(minHeight: 80)
                    .padding(4)
                    .background(Color(.systemGray6))
                    .cornerRadius(8)
                    .onAppear {
                        if draftBody.isEmpty {
                            draftBody = EmailComposer.managementUpdateEmail(for: recording).body
                        }
                    }
                
                Button {
                    showingMail = true
                } label: {
                    Label("Send Update", systemImage: "paperplane.fill")
                        .frame(maxWidth: .infinity)
                        .padding(10)
                        .background(Color.blue)
                        .foregroundStyle(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(.systemGray4), lineWidth: 1)
        )
        .sheet(isPresented: $showingMail) {
            if MailComposeView.canSendMail {
                MailComposeView(
                    emailContent: EmailContent(subject: "Status Update: \(recording.title)", body: draftBody, isHTML: false),
                    recipients: [stakeholder.email],
                    onDismiss: { showingMail = false; isExpanded = false }
                )
            } else {
                 Text("Mail not configured")
            }
        }
    }
}

#Preview {
    NavigationStack {
        RecordingDetailView(recording: .constant(Recording(
            title: "Test Meeting",
            date: Date(),
            duration: 125,
            audioFileName: "test.m4a",
            language: .english,
            transcription: "This is a test transcription of a meeting discussing various topics.",
            summary: MeetingSummary(
                keyPoints: [
                    "Discussed project timeline",
                    "Agreed on new feature priorities",
                    "Budget approved for Q2"
                ],
                actionItems: [
                    ActionItem(task: "Send updated proposal", assignee: "John", deadline: "Friday"),
                    ActionItem(task: "Review design mockups", assignee: "Sarah", deadline: "Next week")
                ],
                futurePoints: [
                    "Follow up on client feedback",
                    "Discuss hiring needs"
                ],
                managementDraft: "The meeting was productive with key decisions made on project priorities."
            ),
            status: .complete
        )))
        .environmentObject(RecordingManager())
    }
}
