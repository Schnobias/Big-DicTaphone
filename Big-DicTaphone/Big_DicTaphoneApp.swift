import SwiftUI

@main
struct Big_DicTaphoneApp: App {
    @StateObject private var recordingManager = RecordingManager()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(recordingManager)
        }
    }
}
