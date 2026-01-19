import SwiftUI
import AVFoundation

/// The main recording interface with animated record button and audio visualization
struct RecordingView: View {
    @EnvironmentObject var recordingManager: RecordingManager
    @State private var selectedLanguage: RecordingLanguage = .english
    @State private var showingPermissionAlert = false
    @State private var currentRecording: Recording?
    @State private var showingSaveSheet = false
    @State private var recordingTitle = ""
    
    private var audioRecorder: AudioRecorder {
        recordingManager.audioRecorder
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [Color(.systemBackground), Color(.systemGray6)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .ignoresSafeArea()
                
                VStack(spacing: 40) {
                    Spacer()
                    
                    // Timer display
                    timerDisplay
                    
                    // Audio level visualization
                    audioLevelView
                    
                    Spacer()
                    
                    // Language selector
                    languageSelector
                    
                    // Record button
                    recordButton
                    
                    // Control buttons (when recording)
                    if audioRecorder.isRecording {
                        controlButtons
                    }
                    
                    Spacer()
                }
                .padding()
            }
            .navigationTitle("Record")
            .navigationBarTitleDisplayMode(.large)
            .alert("Microphone Access Required", isPresented: $showingPermissionAlert) {
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) {
                        UIApplication.shared.open(url)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Please enable microphone access in Settings to record voice notes.")
            }
            .sheet(isPresented: $showingSaveSheet) {
                saveRecordingSheet
            }
            .onAppear {
                Task {
                    _ = await audioRecorder.requestPermission()
                }
            }
        }
    }
    
    // MARK: - Timer Display
    
    private var timerDisplay: some View {
        Text(AudioRecorder.formatTime(audioRecorder.recordingTime))
            .font(.system(size: 72, weight: .thin, design: .monospaced))
            .foregroundStyle(audioRecorder.isRecording ? .primary : .secondary)
            .contentTransition(.numericText())
            .animation(.default, value: audioRecorder.recordingTime)
    }
    
    // MARK: - Audio Level Visualization
    
    private var audioLevelView: some View {
        HStack(spacing: 4) {
            ForEach(0..<20, id: \.self) { index in
                RoundedRectangle(cornerRadius: 2)
                    .fill(barColor(for: index))
                    .frame(width: 8, height: barHeight(for: index))
                    .animation(.easeOut(duration: 0.1), value: audioRecorder.audioLevel)
            }
        }
        .frame(height: 60)
    }
    
    private func barHeight(for index: Int) -> CGFloat {
        let baseHeight: CGFloat = 10
        let maxHeight: CGFloat = 60
        
        guard audioRecorder.isRecording && !audioRecorder.isPaused else {
            return baseHeight
        }
        
        let threshold = Float(index) / 20.0
        let level = audioRecorder.audioLevel
        
        if level > threshold {
            let intensity = CGFloat((level - threshold) / (1.0 - threshold))
            return baseHeight + (maxHeight - baseHeight) * intensity * CGFloat.random(in: 0.8...1.2)
        }
        
        return baseHeight
    }
    
    private func barColor(for index: Int) -> Color {
        let threshold = Float(index) / 20.0
        
        if audioRecorder.audioLevel > threshold {
            if index < 14 {
                return .green
            } else if index < 17 {
                return .yellow
            } else {
                return .red
            }
        }
        
        return Color(.systemGray4)
    }
    
    // MARK: - Language Selector
    
    private var languageSelector: some View {
        HStack(spacing: 16) {
            ForEach(RecordingLanguage.allCases) { language in
                Button {
                    if !audioRecorder.isRecording {
                        selectedLanguage = language
                    }
                } label: {
                    HStack {
                        Text(language.flagEmoji)
                        Text(language.displayName)
                            .font(.subheadline)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(selectedLanguage == language ? Color.accentColor : Color(.systemGray5))
                    )
                    .foregroundStyle(selectedLanguage == language ? .white : .primary)
                }
                .disabled(audioRecorder.isRecording)
            }
        }
    }
    
    // MARK: - Record Button
    
    private var recordButton: some View {
        Button {
            handleRecordButtonTap()
        } label: {
            ZStack {
                // Outer ring
                Circle()
                    .stroke(Color.red.opacity(0.3), lineWidth: 6)
                    .frame(width: 100, height: 100)
                
                // Pulsing ring (when recording)
                if audioRecorder.isRecording && !audioRecorder.isPaused {
                    Circle()
                        .stroke(Color.red.opacity(0.5), lineWidth: 3)
                        .frame(width: 120, height: 120)
                        .scaleEffect(1.0 + CGFloat(audioRecorder.audioLevel) * 0.3)
                        .animation(.easeOut(duration: 0.1), value: audioRecorder.audioLevel)
                }
                
                // Inner button
                Circle()
                    .fill(Color.red)
                    .frame(width: 80, height: 80)
                    .overlay {
                        if audioRecorder.isRecording {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(.white)
                                .frame(width: 30, height: 30)
                        } else {
                            Circle()
                                .fill(.white)
                                .frame(width: 30, height: 30)
                        }
                    }
            }
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.impact(weight: .medium), trigger: audioRecorder.isRecording)
    }
    
    // MARK: - Control Buttons
    
    private var controlButtons: some View {
        HStack(spacing: 60) {
            // Cancel button
            Button {
                audioRecorder.cancelRecording()
                currentRecording = nil
            } label: {
                VStack {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.gray)
                    Text("Cancel")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            
            // Pause/Resume button
            Button {
                if audioRecorder.isPaused {
                    audioRecorder.resumeRecording()
                } else {
                    audioRecorder.pauseRecording()
                }
            } label: {
                VStack {
                    Image(systemName: audioRecorder.isPaused ? "play.circle.fill" : "pause.circle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.orange)
                    Text(audioRecorder.isPaused ? "Resume" : "Pause")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
    
    // MARK: - Save Recording Sheet
    
    private var saveRecordingSheet: some View {
        NavigationStack {
            Form {
                Section("Recording Details") {
                    TextField("Title (optional)", text: $recordingTitle)
                    
                    LabeledContent("Duration") {
                        Text(currentRecording?.formattedDuration ?? "0:00")
                    }
                    
                    LabeledContent("Language") {
                        Text("\(selectedLanguage.flagEmoji) \(selectedLanguage.displayName)")
                    }
                }
                
                Section {
                    Button {
                        saveAndProcess()
                    } label: {
                        HStack {
                            Spacer()
                            Label("Save & Process", systemImage: "waveform.badge.plus")
                            Spacer()
                        }
                    }
                    .foregroundStyle(.white)
                    .listRowBackground(Color.accentColor)
                } footer: {
                    Text("The recording will be transcribed and summarized automatically.")
                }
            }
            .navigationTitle("Save Recording")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Discard") {
                        discardRecording()
                    }
                    .foregroundStyle(.red)
                }
            }
        }
        .presentationDetents([.medium])
    }
    
    // MARK: - Actions
    
    private func handleRecordButtonTap() {
        if audioRecorder.isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private func startRecording() {
        Task {
            let hasPermission = await audioRecorder.requestPermission()
            
            guard hasPermission else {
                showingPermissionAlert = true
                return
            }
            
            do {
                let fileName = recordingManager.generateFileName()
                _ = try audioRecorder.startRecording(fileName: fileName)
                
                currentRecording = Recording(
                    audioFileName: fileName,
                    language: selectedLanguage,
                    status: .recorded
                )
            } catch {
                print("Failed to start recording: \(error)")
            }
        }
    }
    
    private func stopRecording() {
        let duration = audioRecorder.stopRecording()
        
        if var recording = currentRecording {
            recording.duration = duration
            currentRecording = recording
            recordingTitle = ""
            showingSaveSheet = true
        }
    }
    
    private func saveAndProcess() {
        guard var recording = currentRecording else { return }
        
        if !recordingTitle.isEmpty {
            recording.title = recordingTitle
        }
        
        recordingManager.addRecording(recording)
        showingSaveSheet = false
        currentRecording = nil
        
        // Start processing in background
        Task {
            await recordingManager.processRecording(recording)
        }
    }
    
    private func discardRecording() {
        if let recording = currentRecording {
            try? FileManager.default.removeItem(at: recording.audioURL)
        }
        showingSaveSheet = false
        currentRecording = nil
    }
}

#Preview {
    RecordingView()
        .environmentObject(RecordingManager())
}
