import Speech
import Foundation

/// Service for transcribing audio using Apple's Speech framework
class SpeechRecognizer: ObservableObject {
    private var recognitionRequest: SFSpeechURLRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var recognitionCompletion: RecognitionCompletion?
    private var timeoutTask: Task<Void, Never>?
    
    @Published var isTranscribing = false
    @Published var transcriptionProgress: Double = 0
    @Published var authorizationStatus: SFSpeechRecognizerAuthorizationStatus = .notDetermined
    
    init(language: RecordingLanguage = .english) {
        _ = language
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
    func transcribe(audioURL: URL, language: RecordingLanguage, onDeviceOnly: Bool = true) async throws -> String {
        // Create a recognizer for the specific language
        let locale = language == .auto ? Locale.current : Locale(identifier: language.rawValue)
        guard let recognizer = SFSpeechRecognizer(locale: locale) else {
            throw TranscriptionError.languageNotSupported
        }
        
        guard recognizer.isAvailable else {
            throw TranscriptionError.recognizerNotAvailable
        }
        
        if onDeviceOnly && !recognizer.supportsOnDeviceRecognition {
            throw TranscriptionError.recognitionFailed("On-device recognition is not available for this language. Choose an installed language; no audio was uploaded.")
        }
        await MainActor.run {
            self.isTranscribing = true
            self.transcriptionProgress = 0
        }
        
        defer {
            recognitionTask = nil
            recognitionCompletion = nil
            timeoutTask?.cancel()
            timeoutTask = nil
            Task { @MainActor in
                self.isTranscribing = false
                self.transcriptionProgress = 1.0
            }
        }
        
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
            let completion = RecognitionCompletion(continuation)
            recognitionCompletion = completion
            let request = SFSpeechURLRecognitionRequest(url: audioURL)
            request.shouldReportPartialResults = false
            request.addsPunctuation = true
            
            if #available(iOS 16.0, *) {
                request.requiresOnDeviceRecognition = onDeviceOnly
            }
            
            recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
                if let error = error {
                    completion.finish(.failure(TranscriptionError.recognitionFailed(error.localizedDescription)))
                    return
                }
                
                guard let result = result else {
                    return
                }
                
                if result.isFinal {
                    let transcription = result.bestTranscription.formattedString
                    completion.finish(transcription.isEmpty ? .failure(TranscriptionError.noResult) : .success(transcription))
                } else {
                    // Update progress based on segments
                    Task { @MainActor in
                        let segments = Double(result.bestTranscription.segments.count)
                        self?.transcriptionProgress = min(segments / 100.0, 0.9) // Cap at 90% until final
                    }
                }
            }
                self.timeoutTask = Task { [weak self, weak completion] in
                    try? await Task.sleep(nanoseconds: 10 * 60 * 1_000_000_000)
                    guard !Task.isCancelled else { return }
                    completion?.finish(.failure(TranscriptionError.recognitionTimedOut))
                    self?.recognitionTask?.cancel()
                }
            }
        }, onCancel: {
            self.cancelTranscription()
        })
    }
    
    /// Cancel any ongoing transcription
    func cancelTranscription() {
        recognitionCompletion?.finish(.failure(TranscriptionError.cancelled))
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionCompletion = nil
        timeoutTask?.cancel()
        timeoutTask = nil
        isTranscribing = false
    }
    
    /// Check if a language is supported on this device
    static func isLanguageSupported(_ language: RecordingLanguage) -> Bool {
        let locale = language == .auto ? Locale.current : Locale(identifier: language.rawValue)
        return SFSpeechRecognizer(locale: locale)?.isAvailable ?? false
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
    case cancelled
    case recognitionTimedOut
    
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
        case .cancelled:
            return "Speech recognition was cancelled."
        case .recognitionTimedOut:
            return "Speech recognition timed out. Please try a shorter recording."
        }
    }
}

/// Speech services may deliver more than one terminal callback.
final class RecognitionCompletion {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<String, Error>?
    init(_ continuation: CheckedContinuation<String, Error>) { self.continuation = continuation }
    func finish(_ result: Result<String, Error>) {
        lock.lock()
        let pending = continuation
        continuation = nil
        lock.unlock()
        pending?.resume(with: result)
    }
}
