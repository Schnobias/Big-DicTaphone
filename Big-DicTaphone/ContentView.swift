import SwiftUI

struct ContentView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            RecordingView()
                .tabItem {
                    Label("Record", systemImage: "mic.fill")
                }
                .tag(0)
            
            RecordingListView()
                .tabItem {
                    Label("Recordings", systemImage: "list.bullet")
                }
                .tag(1)
            
            ToDoListView()
                .tabItem {
                    Label("To-Do", systemImage: "checklist")
                }
                .tag(2)
            
            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
                .tag(3)
        }
        .tint(.red)
    }
}

#Preview {
    ContentView()
        .environmentObject(RecordingManager())
}
