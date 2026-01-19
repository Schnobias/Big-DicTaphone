import MessageUI
import SwiftUI

/// Service for composing and sending emails using the device's mail app
struct EmailComposer {
    
    /// Generate email content for a personal summary
    static func personalSummaryEmail(for recording: Recording) -> EmailContent {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        formatter.timeStyle = .short
        let dateStr = formatter.string(from: recording.date)
        
        var content = """
        📝 \(recording.title)
        
        Recorded: \(dateStr)
        Duration: \(recording.formattedDuration)
        Language: \(recording.language.flagEmoji) \(recording.language.displayName)
        
        """
        
        if let summary = recording.summary {
            if let quote = summary.funnyQuote {
                content += """
                💡 QUOTE OF THE MEETING
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                "\(quote)"
                
                """
            }
            
            if !summary.keyPoints.isEmpty {
                content += """
                🎯 KEY POINTS
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                \(summary.keyPoints.map { "• \($0)" }.joined(separator: "\n"))
                
                """
            }
            
            if !summary.actionItems.isEmpty {
                content += """
                ✅ ACTION ITEMS
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                \(summary.actionItems.map { $0.formattedDescription.hasPrefix("•") ? $0.formattedDescription : "• \($0.formattedDescription)" }.joined(separator: "\n"))
                
                """
            }
            
            if !summary.futurePoints.isEmpty {
                content += """
                📅 FUTURE DISCUSSION
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                \(summary.futurePoints.map { "• \($0)" }.joined(separator: "\n"))
                
                """
            }
        }
        
        if let transcription = recording.transcription {
            content += """
            📜 FULL TRANSCRIPTION
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            \(transcription)
            """
        }
        
        content += """
        
        —
        Sent from Big DicTa
        """
        
        return EmailContent(
            subject: "Meeting Summary: \(recording.title)",
            body: content,
            isHTML: false
        )
    }
    
    /// Generate email content for a management update
    static func managementUpdateEmail(for recording: Recording) -> EmailContent {
        let formatter = DateFormatter()
        formatter.dateStyle = .long
        let dateStr = formatter.string(from: recording.date)
        
        var content = """
        📊 Status Update: \(recording.title)
        Date: \(dateStr)
        
        """
        
        if let summary = recording.summary {
            if let draft = summary.managementDraft {
                content += "\(draft)\n\n"
            }
            
            if !summary.actionItems.isEmpty {
                content += """
                KEY ACTION ITEMS:
                \(summary.actionItems.prefix(5).map { "• \($0.formattedDescription)" }.joined(separator: "\n"))
                
                """
            }
        }
        
        content += """
        —
        Sent from Big DicTa
        """
        
        return EmailContent(
            subject: "Status Update: \(recording.title)",
            body: content,
            isHTML: false
        )
    }
}

/// Email content to be composed
struct EmailContent {
    let subject: String
    let body: String
    let isHTML: Bool
}

/// SwiftUI wrapper for MFMailComposeViewController
struct MailComposeView: UIViewControllerRepresentable {
    let emailContent: EmailContent
    let recipients: [String]
    let onDismiss: () -> Void
    
    func makeUIViewController(context: Context) -> MFMailComposeViewController {
        let composer = MFMailComposeViewController()
        composer.mailComposeDelegate = context.coordinator
        composer.setSubject(emailContent.subject)
        composer.setToRecipients(recipients)
        composer.setMessageBody(emailContent.body, isHTML: emailContent.isHTML)
        return composer
    }
    
    func updateUIViewController(_ uiViewController: MFMailComposeViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onDismiss: onDismiss)
    }
    
    class Coordinator: NSObject, MFMailComposeViewControllerDelegate {
        let onDismiss: () -> Void
        
        init(onDismiss: @escaping () -> Void) {
            self.onDismiss = onDismiss
        }
        
        func mailComposeController(_ controller: MFMailComposeViewController, didFinishWith result: MFMailComposeResult, error: Error?) {
            onDismiss()
        }
    }
}

/// Check if the device can send emails
extension MailComposeView {
    static var canSendMail: Bool {
        MFMailComposeViewController.canSendMail()
    }
}
