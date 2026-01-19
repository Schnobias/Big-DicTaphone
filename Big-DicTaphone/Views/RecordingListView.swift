import SwiftUI

/// List view showing all saved recordings
struct RecordingListView: View {
    @EnvironmentObject var recordingManager: RecordingManager
    @State private var selectedRecording: Recording?
    
    var body: some View {
        NavigationStack {
            Group {
                if recordingManager.recordings.isEmpty {
                    emptyState
                } else {
                    recordingsList
                }
            }
            .navigationTitle("Recordings")
            .navigationDestination(item: $selectedRecording) { recording in
                RecordingDetailView(recording: binding(for: recording))
            }
            .overlay {
                if recordingManager.isProcessing {
                    processingOverlay
                }
            }
        }
    }
    
    // MARK: - Empty State
    
    private var emptyState: some View {
        ContentUnavailableView {
            Label("No Recordings", systemImage: "waveform")
        } description: {
            Text("Your voice notes and meeting recordings will appear here.")
        } actions: {
            Text("Go to the Record tab to create your first recording")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
    
    // MARK: - Recordings List
    
    private var recordingsList: some View {
        List {
            ForEach(recordingManager.recordings) { recording in
                RecordingRow(recording: recording)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        selectedRecording = recording
                    }
            }
            .onDelete(perform: recordingManager.deleteRecordings)
        }
        .listStyle(.insetGrouped)
    }
    
    // MARK: - Processing Overlay
    
    private var processingOverlay: some View {
        VStack(spacing: 16) {
            ProgressView()
                .scaleEffect(1.5)
            
            Text(recordingManager.processingMessage)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .padding(32)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
    }
    
    // MARK: - Helpers
    
    private func binding(for recording: Recording) -> Binding<Recording> {
        Binding<Recording>(
            get: {
                recordingManager.recordings.first { $0.id == recording.id } ?? recording
            },
            set: { newValue in
                recordingManager.updateRecording(newValue)
            }
        )
    }
}

/// A single row in the recordings list
struct RecordingRow: View {
    let recording: Recording
    
    var body: some View {
        HStack(spacing: 16) {
            // Status indicator
            statusIcon
            
            // Recording info
            VStack(alignment: .leading, spacing: 4) {
                Text(recording.title)
                    .font(.headline)
                    .lineLimit(1)
                
                HStack(spacing: 8) {
                    Text(recording.language.flagEmoji)
                    
                    Text(formattedDate)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    
                    Text("•")
                        .foregroundStyle(.secondary)
                    
                    Text(recording.formattedDuration)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            
            Spacer()
            
            // Status badge
            statusBadge
        }
        .padding(.vertical, 8)
    }
    
    private var statusIcon: some View {
        ZStack {
            Circle()
                .fill(statusColor.opacity(0.2))
                .frame(width: 44, height: 44)
            
            Image(systemName: statusIconName)
                .font(.system(size: 18))
                .foregroundStyle(statusColor)
        }
    }
    
    private var statusBadge: some View {
        Group {
            if recording.status.isProcessing {
                ProgressView()
                    .scaleEffect(0.8)
            } else if recording.status == .complete {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            } else if recording.status == .failed {
                Image(systemName: "exclamationmark.circle.fill")
                    .foregroundStyle(.red)
            }
        }
    }
    
    private var statusIconName: String {
        switch recording.status {
        case .recorded:
            return "waveform"
        case .transcribing:
            return "text.bubble"
        case .summarizing:
            return "sparkles"
        case .complete:
            return "doc.text.fill"
        case .failed:
            return "exclamationmark.triangle"
        }
    }
    
    private var statusColor: Color {
        switch recording.status {
        case .recorded:
            return .gray
        case .transcribing, .summarizing:
            return .orange
        case .complete:
            return .green
        case .failed:
            return .red
        }
    }
    
    private var formattedDate: String {
        let formatter = DateFormatter()
        
        if Calendar.current.isDateInToday(recording.date) {
            formatter.dateFormat = "'Today' HH:mm"
        } else if Calendar.current.isDateInYesterday(recording.date) {
            formatter.dateFormat = "'Yesterday' HH:mm"
        } else {
            formatter.dateStyle = .medium
            formatter.timeStyle = .short
        }
        
        return formatter.string(from: recording.date)
    }
}

#Preview {
    RecordingListView()
        .environmentObject(RecordingManager())
}
