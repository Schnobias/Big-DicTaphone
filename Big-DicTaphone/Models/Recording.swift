import Foundation

/// Represents a single voice recording with its metadata and processing status
struct Recording: Identifiable, Codable {
    let id: UUID
    var title: String
    let date: Date
    var duration: TimeInterval
    let audioFileName: String
    let language: RecordingLanguage
    var transcription: String?
    var summary: MeetingSummary?
    var status: ProcessingStatus
    
    init(
        id: UUID = UUID(),
        title: String = "",
        date: Date = Date(),
        duration: TimeInterval = 0,
        audioFileName: String,
        language: RecordingLanguage = .auto,
        transcription: String? = nil,
        summary: MeetingSummary? = nil,
        status: ProcessingStatus = .recorded
    ) {
        self.id = id
        self.title = title.isEmpty ? Self.defaultTitle(for: date) : title
        self.date = date
        self.duration = duration
        self.audioFileName = audioFileName
        self.language = language
        self.transcription = transcription
        self.summary = summary
        self.status = status
    }
    
    /// Generate a default title based on the recording date
    static func defaultTitle(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return "Recording \(formatter.string(from: date))"
    }
    
    /// Get the full URL for the audio file
    var audioURL: URL {
        Recording.recordingsDirectory.appendingPathComponent(audioFileName)
    }
    
    /// Directory where all recordings are stored
    static var recordingsDirectory: URL {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let recordingsPath = documentsPath.appendingPathComponent("Recordings")
        
        // Create directory if it doesn't exist
        if !FileManager.default.fileExists(atPath: recordingsPath.path) {
            try? FileManager.default.createDirectory(at: recordingsPath, withIntermediateDirectories: true)
        }
        
        return recordingsPath
    }
    
    /// Formatted duration string (MM:SS or HH:MM:SS)
    var formattedDuration: String {
        let hours = Int(duration) / 3600
        let minutes = (Int(duration) % 3600) / 60
        let seconds = Int(duration) % 60
        
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%d:%02d", minutes, seconds)
        }
    }
}

/// Supported languages for transcription
enum RecordingLanguage: String, Codable, CaseIterable, Identifiable {
    case auto = "auto"
    case dutch = "nl-NL"
    case english = "en-US"
    case englishUK = "en-GB"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .auto: return "Auto-detect"
        case .dutch: return "Nederlands"
        case .english: return "English (US)"
        case .englishUK: return "English (UK)"
        }
    }
    
    var flagEmoji: String {
        switch self {
        case .auto: return "🌐"
        case .dutch: return "🇳🇱"
        case .english: return "🇺🇸"
        case .englishUK: return "🇬🇧"
        }
    }
}

/// Processing status of a recording
enum ProcessingStatus: String, Codable {
    case recorded = "Recorded"
    case transcribing = "Transcribing..."
    case summarizing = "Summarizing..."
    case complete = "Complete"
    case failed = "Failed"
    case mediaMissing = "Audio Missing"
    
    var color: String {
        switch self {
        case .recorded: return "gray"
        case .transcribing, .summarizing: return "orange"
        case .complete: return "green"
        case .failed, .mediaMissing: return "red"
        }
    }
    
    var isProcessing: Bool {
        self == .transcribing || self == .summarizing
    }
}
