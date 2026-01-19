import AVFoundation
import Foundation

/// Service for recording audio using AVFoundation
class AudioRecorder: NSObject, ObservableObject {
    private var audioRecorder: AVAudioRecorder?
    private var recordingSession: AVAudioSession?
    private var timer: Timer?
    private var startTime: Date?
    
    @Published var isRecording = false
    @Published var isPaused = false
    @Published var recordingTime: TimeInterval = 0
    @Published var audioLevel: Float = 0
    @Published var permissionGranted = false
    
    override init() {
        super.init()
        setupAudioSession()
    }
    
    /// Setup the audio session for recording
    private func setupAudioSession() {
        recordingSession = AVAudioSession.sharedInstance()
        
        do {
            try recordingSession?.setCategory(.playAndRecord, mode: .measurement, options: [.defaultToSpeaker, .allowBluetooth])
            try recordingSession?.setActive(true)
        } catch {
            print("Failed to setup audio session: \(error)")
        }
    }
    
    /// Request microphone permission
    func requestPermission() async -> Bool {
        if #available(iOS 17.0, *) {
            let granted = await AVAudioApplication.requestRecordPermission()
            await MainActor.run {
                self.permissionGranted = granted
            }
            return granted
        } else {
            return await withCheckedContinuation { continuation in
                AVAudioSession.sharedInstance().requestRecordPermission { granted in
                    Task { @MainActor in
                        self.permissionGranted = granted
                    }
                    continuation.resume(returning: granted)
                }
            }
        }
    }
    
    /// Start recording with the given filename
    func startRecording(fileName: String) throws -> URL {
        let audioURL = Recording.recordingsDirectory.appendingPathComponent(fileName)
        
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 44100,
            AVNumberOfChannelsKey: 2, // Stereo for better speaker separation
            AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
        ]
        
        do {
            audioRecorder = try AVAudioRecorder(url: audioURL, settings: settings)
            audioRecorder?.delegate = self
            audioRecorder?.isMeteringEnabled = true
            audioRecorder?.record()
            
            isRecording = true
            isPaused = false
            startTime = Date()
            recordingTime = 0
            
            startTimer()
            
            return audioURL
        } catch {
            print("Failed to start recording: \(error)")
            throw error
        }
    }
    
    /// Pause the current recording
    func pauseRecording() {
        audioRecorder?.pause()
        isPaused = true
        timer?.invalidate()
    }
    
    /// Resume a paused recording
    func resumeRecording() {
        audioRecorder?.record()
        isPaused = false
        startTimer()
    }
    
    /// Stop recording and return the duration
    func stopRecording() -> TimeInterval {
        timer?.invalidate()
        timer = nil
        
        let duration = recordingTime
        
        audioRecorder?.stop()
        audioRecorder = nil
        
        isRecording = false
        isPaused = false
        audioLevel = 0
        
        return duration
    }
    
    /// Cancel recording and delete the file
    func cancelRecording() {
        timer?.invalidate()
        timer = nil
        
        if let recorder = audioRecorder {
            let url = recorder.url
            recorder.stop()
            try? FileManager.default.removeItem(at: url)
        }
        
        audioRecorder = nil
        isRecording = false
        isPaused = false
        recordingTime = 0
        audioLevel = 0
    }
    
    /// Start the timer to update recording time and audio levels
    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            guard let self = self, let recorder = self.audioRecorder else { return }
            
            if !self.isPaused {
                self.recordingTime += 0.1
            }
            
            recorder.updateMeters()
            let normalizedLevel = self.normalizedPowerLevel(from: recorder.averagePower(forChannel: 0))
            
            DispatchQueue.main.async {
                self.audioLevel = normalizedLevel
            }
        }
    }
    
    /// Normalize the power level to 0-1 range
    private func normalizedPowerLevel(from decibels: Float) -> Float {
        // Audio power is typically between -160 and 0 dB
        // We normalize to 0-1 range for visualization
        let minDb: Float = -60
        let maxDb: Float = 0
        
        let clampedDb = max(min(decibels, maxDb), minDb)
        return (clampedDb - minDb) / (maxDb - minDb)
    }
    
    /// Format time interval as MM:SS or HH:MM:SS
    static func formatTime(_ time: TimeInterval) -> String {
        let hours = Int(time) / 3600
        let minutes = (Int(time) % 3600) / 60
        let seconds = Int(time) % 60
        
        if hours > 0 {
            return String(format: "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String(format: "%02d:%02d", minutes, seconds)
        }
    }
}

extension AudioRecorder: AVAudioRecorderDelegate {
    func audioRecorderDidFinishRecording(_ recorder: AVAudioRecorder, successfully flag: Bool) {
        if !flag {
            print("Recording failed")
        }
    }
    
    func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        if let error = error {
            print("Recording encode error: \(error)")
        }
    }
}
