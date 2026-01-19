import Speech
import Foundation

/// Service for transcribing audio using Apple's Speech framework
class SpeechRecognizer: ObservableObject {
    private let speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechURLRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    
    @Published var isTranscribing = false
    @Published var transcriptionProgress: Double = 0
    @Published var authorizationStatus: SFSpeechRecognizerAuthorizationStatus = .notDetermined
    
    init(language: RecordingLanguage = .english) {
        self.speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: language.rawValue))
    }
    
    /// Request speech recognition authorization
    func requestAuthorization() async -> Bool {
        return await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                Task { @MainActor in
                    self.authorizationStatus = status
                }
                continuation.resume(returning: status == .authorized)
            }
        }
    }
    
    /// Transcribe an audio file at the given URL
    func transcribe(audioURL: URL, language: RecordingLanguage) async throws -> String {
        // Create a recognizer for the specific language
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: language.rawValue)) else {
            throw TranscriptionError.languageNotSupported
        }
        
        guard recognizer.isAvailable else {
            throw TranscriptionError.recognizerNotAvailable
        }
        
        await MainActor.run {
            self.isTranscribing = true
            self.transcriptionProgress = 0
        }
        
        defer {
            Task { @MainActor in
                self.isTranscribing = false
                self.transcriptionProgress = 1.0
            }
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            let request = SFSpeechURLRecognitionRequest(url: audioURL)
            request.shouldReportPartialResults = false
            request.addsPunctuation = true
            
            if #available(iOS 16.0, *) {
                request.requiresOnDeviceRecognition = false // Allow cloud for better accuracy
            }
            
            recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
                if let error = error {
                    continuation.resume(throwing: TranscriptionError.recognitionFailed(error.localizedDescription))
                    return
                }
                
                guard let result = result else {
                    continuation.resume(throwing: TranscriptionError.noResult)
                    return
                }
                
                if result.isFinal {
                    let transcription = result.bestTranscription.formattedString
                    continuation.resume(returning: transcription)
                } else {
                    // Update progress based on segments
                    Task { @MainActor in
                        let segments = Double(result.bestTranscription.segments.count)
                        self?.transcriptionProgress = min(segments / 100.0, 0.9) // Cap at 90% until final
                    }
                }
            }
        }
    }
    
    /// Cancel any ongoing transcription
    func cancelTranscription() {
        recognitionTask?.cancel()
        recognitionTask = nil
        isTranscribing = false
    }
    
    /// Check if a language is supported on this device
    static func isLanguageSupported(_ language: RecordingLanguage) -> Bool {
        return SFSpeechRecognizer(locale: Locale(identifier: language.rawValue))?.isAvailable ?? false
    }
    
    /// Get all supported languages
    static var supportedLanguages: [RecordingLanguage] {
        RecordingLanguage.allCases.filter { isLanguageSupported($0) }
    }
}

/// Errors that can occur during transcription
enum TranscriptionError: LocalizedError {
    case languageNotSupported
    case recognizerNotAvailable
    case recognitionFailed(String)
    case noResult
    
    var errorDescription: String? {
        switch self {
        case .languageNotSupported:
            return "This language is not supported for speech recognition on this device."
        case .recognizerNotAvailable:
            return "Speech recognition is not available at this time. Please try again later."
        case .recognitionFailed(let message):
            return "Speech recognition failed: \(message)"
        case .noResult:
            return "No speech was detected in the recording."
        }
    }
}
