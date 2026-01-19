package com.bigdictaphone.app.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import com.bigdictaphone.app.data.Recording
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for composing and sending emails
 */
class EmailService(private val context: Context) {
    
    /**
     * Open email client with personal summary
     */
    fun sendPersonalSummary(recording: Recording, recipientEmail: String) {
        val content = createPersonalSummaryContent(recording)
        openEmailClient(
            recipient = recipientEmail,
            subject = "Meeting Summary: ${recording.title}",
            body = content
        )
    }
    
    /**
     * Open email client with management update
     */
    fun sendManagementUpdate(recording: Recording, recipients: List<String> = emptyList()) {
        val content = createManagementUpdateContent(recording)
        openEmailClient(
            recipient = recipients.firstOrNull() ?: "",
            subject = "Status Update: ${recording.title}",
            body = content,
            cc = recipients.drop(1)
        )
    }

    /**
     * Send an email with custom content
     */
    fun sendEmail(recipient: String, subject: String, body: String) {
        openEmailClient(
            recipient = recipient,
            subject = subject,
            body = body
        )
    }
    
    /**
     * Open email client with the given content
     */
    private fun openEmailClient(
        recipient: String,
        subject: String,
        body: String,
        cc: List<String> = emptyList()
    ) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            if (cc.isNotEmpty()) {
                putExtra(Intent.EXTRA_CC, cc.toTypedArray())
            }
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to ACTION_SEND if no email client handles mailto:
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                if (cc.isNotEmpty()) {
                    putExtra(Intent.EXTRA_CC, cc.toTypedArray())
                }
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Send Email").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
    
    /**
     * Create personal summary email content
     */
    fun createPersonalSummaryContent(recording: Recording): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val summary = recording.summary
        
        return buildString {
            appendLine("📝 ${recording.title}")
            appendLine()
            appendLine("Recorded: ${dateFormat.format(Date(recording.timestamp))}")
            appendLine("Duration: ${recording.formattedDuration}")
            appendLine("Language: ${recording.language.flag} ${recording.language.displayName}")
            appendLine()
            
            if (summary != null) {
                // Add funny quote if available
                summary.funnyQuote?.let { quote ->
                    appendLine("💡 QUOTE OF THE MEETING")
                    appendLine("─".repeat(30))
                    appendLine("\"$quote\"")
                    appendLine()
                }

                if (summary.keyPoints.isNotEmpty()) {
                    appendLine("🎯 KEY POINTS")
                    appendLine("─".repeat(30))
                    summary.keyPoints.forEach { point ->
                        appendLine("• $point")
                    }
                    appendLine()
                }
                
                if (summary.actionItems.isNotEmpty()) {
                    appendLine("✅ ACTION ITEMS")
                    appendLine("─".repeat(30))
                    summary.actionItems.forEach { item ->
                        append("• ${item.task}")
                        item.assignee?.takeIf { it.isNotBlank() }?.let { append(" → $it") }
                        item.deadline?.takeIf { it.isNotBlank() }?.let { append(" (by $it)") }
                        appendLine()
                    }
                    appendLine()
                }
                
                if (summary.futurePoints.isNotEmpty()) {
                    appendLine("📅 FUTURE DISCUSSION")
                    appendLine("─".repeat(30))
                    summary.futurePoints.forEach { point ->
                        appendLine("• $point")
                    }
                    appendLine()
                }
            }
            
            recording.transcription?.let { transcription ->
                appendLine("📜 FULL TRANSCRIPTION")
                appendLine("─".repeat(30))
                appendLine(transcription)
            }
            
            appendLine()
            appendLine("—")
            appendLine("Sent from Big-DicTaphone")
        }
    }
    
    /**
     * Create management update email content
     */
    /**
     * Create management update email content
     */
    fun createManagementUpdateContent(recording: Recording): String {
        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        val summary = recording.summary
        
        return buildString {
            appendLine("📊 Status Update: ${recording.title}")
            appendLine("Date: ${dateFormat.format(Date(recording.timestamp))}")
            appendLine()
            
            summary?.managementDraft?.let { draft ->
                appendLine(draft)
                appendLine()
            }
            
            if (summary != null && summary.actionItems.isNotEmpty()) {
                appendLine("KEY ACTION ITEMS:")
                summary.actionItems.take(5).forEach { item ->
                    append("• ${item.task}")
                    item.assignee?.takeIf { it.isNotBlank() }?.let { append(" → $it") }
                    item.deadline?.takeIf { it.isNotBlank() }?.let { append(" (by $it)") }
                    appendLine()
                }
                appendLine()
            }
            
            appendLine("—")
            appendLine("Sent from Big-DicTaphone")
        }
    }
}
