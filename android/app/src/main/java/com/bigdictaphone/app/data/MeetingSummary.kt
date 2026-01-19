package com.bigdictaphone.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Summary generated from a meeting recording
 */
@Serializable
data class MeetingSummary(
    val keyPoints: List<String> = emptyList(),
    val actionItems: List<ActionItem> = emptyList(),
    val futurePoints: List<String> = emptyList(),
    val managementDraft: String? = null,
    val funnyQuote: String? = null  // Playful/humorous quote related to the meeting
) {
    val isEmpty: Boolean
        get() = keyPoints.isEmpty() && actionItems.isEmpty() && futurePoints.isEmpty()
}

/**
 * A single action item from the meeting
 */
@Serializable
data class ActionItem(
    val id: String = UUID.randomUUID().toString(),
    val task: String,
    val assignee: String? = null,
    val deadline: String? = null,
    val completed: Boolean = false,
    val recordingId: String? = null  // Link back to source recording
) {
    val formattedDescription: String
        get() = buildString {
            append(task)
            assignee?.takeIf { it.isNotBlank() }?.let { append(" → $it") }
            deadline?.takeIf { it.isNotBlank() }?.let { append(" (by $it)") }
        }
}

/**
 * Response structure from Gemini API
 */
@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>? = null,
    val error: GeminiError? = null
) {
    val text: String?
        get() = candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

@Serializable
data class Candidate(
    val content: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>? = null
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GeminiError(
    val message: String? = null,
    val code: Int? = null
)

/**
 * Parsed summary from Gemini response
 */
@Serializable
data class ParsedSummary(
    val keyPoints: List<String>? = null,
    val actionItems: List<ParsedActionItem>? = null,
    val futurePoints: List<String>? = null,
    val managementDraft: String? = null,
    val funnyQuote: String? = null
) {
    fun toMeetingSummary(): MeetingSummary = MeetingSummary(
        keyPoints = keyPoints ?: emptyList(),
        actionItems = (actionItems ?: emptyList()).map { 
            ActionItem(task = it.task, assignee = it.assignee, deadline = it.deadline)
        },
        futurePoints = futurePoints ?: emptyList(),
        managementDraft = managementDraft,
        funnyQuote = funnyQuote
    )
}

@Serializable
data class ParsedActionItem(
    val task: String,
    val assignee: String? = null,
    val deadline: String? = null
)
