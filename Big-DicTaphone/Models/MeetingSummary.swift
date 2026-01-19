import Foundation

/// Summary generated from a meeting recording
struct MeetingSummary: Codable, Equatable {
    /// Key points discussed in the meeting
    let keyPoints: [String]
    
    /// Action items with optional assignees and deadlines
    let actionItems: [ActionItem]
    
    /// Points to discuss in future meetings
    let futurePoints: [String]
    
    /// Pre-formatted management update draft
    let managementDraft: String?
    
    /// Playful one-liner related to the meeting content
    let funnyQuote: String?
    
    /// Check if the summary is empty
    var isEmpty: Bool {
        keyPoints.isEmpty && actionItems.isEmpty && futurePoints.isEmpty
    }
}

/// A single action item from the meeting
struct ActionItem: Codable, Identifiable, Equatable {
    let id: UUID
    let task: String
    let assignee: String?
    let deadline: String?
    var completed: Bool
    var recordingId: String? // String to avoid circular dependency or complex encoding
    
    init(id: UUID = UUID(), task: String, assignee: String? = nil, deadline: String? = nil, completed: Bool = false, recordingId: String? = nil) {
        self.id = id
        self.task = task
        self.assignee = assignee
        self.deadline = deadline
        self.completed = completed
        self.recordingId = recordingId
    }
    
    /// Formatted string for display
    var formattedDescription: String {
        var parts = [task]
        if let assignee = assignee, !assignee.isEmpty {
            parts.append("→ \(assignee)")
        }
        if let deadline = deadline, !deadline.isEmpty {
            parts.append("(by \(deadline))")
        }
        return parts.joined(separator: " ")
    }
}

/// Response structure from Gemini API
struct GeminiResponse: Codable {
    let candidates: [Candidate]?
    let error: GeminiError?
    
    struct Candidate: Codable {
        let content: Content?
    }
    
    struct Content: Codable {
        let parts: [Part]?
    }
    
    struct Part: Codable {
        let text: String?
    }
    
    struct GeminiError: Codable {
        let message: String?
        let code: Int?
    }
    
    /// Extract the text response
    var text: String? {
        candidates?.first?.content?.parts?.first?.text
    }
}

/// Parsed summary from Gemini response
struct ParsedSummary: Codable {
    let keyPoints: [String]?
    let actionItems: [ParsedActionItem]?
    let futurePoints: [String]?
    let managementDraft: String?
    let funnyQuote: String?
    
    struct ParsedActionItem: Codable {
        let task: String
        let assignee: String?
        let deadline: String?
    }
    
    /// Convert to MeetingSummary
    func toMeetingSummary() -> MeetingSummary {
        MeetingSummary(
            keyPoints: keyPoints ?? [],
            actionItems: (actionItems ?? []).map { item in
                ActionItem(task: item.task, assignee: item.assignee, deadline: item.deadline)
            },
            futurePoints: futurePoints ?? [],
            managementDraft: managementDraft,
            funnyQuote: funnyQuote
        )
    }
}
