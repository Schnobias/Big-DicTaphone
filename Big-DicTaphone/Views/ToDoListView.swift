import SwiftUI

struct ToDoListView: View {
    @EnvironmentObject var recordingManager: RecordingManager
    @State private var showingCompleted = false
    
    var openActionItems: [ActionItem] {
        recordingManager.allActionItems.filter { !$0.completed }
    }
    
    var completedActionItems: [ActionItem] {
        recordingManager.allActionItems.filter { $0.completed }
    }
    
    var body: some View {
        NavigationStack {
            List {
                // Stats Header
                Section {
                    HStack {
                        VStack {
                            Text("\(recordingManager.allActionItems.count)")
                                .font(.title)
                                .fontWeight(.bold)
                            Text("Total")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        
                        Divider()
                        
                        VStack {
                            Text("\(openActionItems.count)")
                                .font(.title)
                                .fontWeight(.bold)
                                .foregroundStyle(openActionItems.isEmpty ? .secondary : .orange)
                            Text("Pending")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                        
                        Divider()
                        
                        VStack {
                            Text("\(completedActionItems.count)")
                                .font(.title)
                                .fontWeight(.bold)
                                .foregroundStyle(.green)
                            Text("Done")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.vertical, 8)
                }
                
                // Pending Items
                Section("Pending") {
                    if openActionItems.isEmpty {
                        Text("No pending action items 🎉")
                            .foregroundStyle(.secondary)
                            .italic()
                    } else {
                        ForEach(openActionItems) { item in
                            ToDoRow(item: item)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    withAnimation {
                                        recordingManager.toggleActionItemCompletion(item, in: nil)
                                    }
                                }
                        }
                    }
                }
                
                // Completed Items
                Section("Completed") {
                    if showingCompleted {
                        if completedActionItems.isEmpty {
                            Text("No completed items yet")
                                .foregroundStyle(.secondary)
                                .italic()
                        } else {
                            ForEach(completedActionItems) { item in
                                ToDoRow(item: item)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        withAnimation {
                                            recordingManager.toggleActionItemCompletion(item, in: nil)
                                        }
                                    }
                            }
                        }
                    } else if !completedActionItems.isEmpty {
                        Button("Show \(completedActionItems.count) completed items") {
                            withAnimation { showingCompleted = true }
                        }
                    }
                }
            }
            .navigationTitle("Action Items")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                     if showingCompleted {
                        Button("Hide Done") {
                            withAnimation { showingCompleted = false }
                        }
                     }
                }
            }
        }
    }
}

struct ToDoRow: View {
    let item: ActionItem
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: item.completed ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .foregroundStyle(item.completed ? .green : .secondary)
                .padding(.top, 2)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(item.task)
                    .font(.body)
                    .strikethrough(item.completed)
                    .foregroundStyle(item.completed ? .secondary : .primary)
                
                HStack(spacing: 12) {
                    if let assignee = item.assignee, !assignee.isEmpty {
                        Label(assignee, systemImage: "person.fill")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                    
                    if let deadline = item.deadline, !deadline.isEmpty {
                        Label(deadline, systemImage: "calendar")
                            .font(.caption)
                            .foregroundStyle(item.completed ? .secondary : .green)
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    ToDoListView()
        .environmentObject(RecordingManager())
}
