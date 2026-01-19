import Foundation

/// Service for interacting with Google's Gemini Flash API for AI summarization
class GeminiService {
    private let baseURL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    
    /// Generate a meeting summary from a transcription
    func generateSummary(transcription: String, language: RecordingLanguage) async throws -> MeetingSummary {
        guard let apiKey = getAPIKey(), !apiKey.isEmpty else {
            throw GeminiError.noAPIKey
        }
        
        let prompt = createPrompt(for: transcription, language: language)
        let response = try await sendRequest(prompt: prompt, apiKey: apiKey)
        
        guard let text = response.text else {
            if let error = response.error {
                throw GeminiError.apiError(error.message ?? "Unknown error")
            }
            throw GeminiError.noResponse
        }
        
        return try parseResponse(text)
    }
    
    /// Create a structured prompt for the AI
    private func createPrompt(for transcription: String, language: RecordingLanguage) -> String {
        let languageInstruction: String
        switch language {
        case .auto: languageInstruction = "Detect the language automatically and transcribe in that language"
        case .dutch: languageInstruction = "Transcribe in Dutch (Nederlands)"
        default: languageInstruction = "Transcribe in English"
        }
        
        return """
        Please listen to this audio recording (transcription below) and:
        1. First, \(languageInstruction). IMPORTANT: If possible, identify different speakers and label them as "Speaker 1:", "Speaker 2:", etc.
        2. Then, provide a structured summary
        
        TRANSCRIPTION:
        \(transcription)
        
        Please respond with a JSON object in this exact format (no markdown, just raw JSON):
        {
            "keyPoints": ["point 1", "point 2", ...],
            "actionItems": [
                {"task": "description", "assignee": "person name or null", "deadline": "date/timeframe or null"}
            ],
            "futurePoints": ["topic 1", "topic 2", ...],
            "managementDraft": "A concise 2-3 paragraph executive summary suitable for upper management, focusing on key decisions, progress, and any issues that need attention.",
            "funnyQuote": "A playful, witty, or humorous one-liner related to the meeting content. Be creative and make it memorable!"
        }
        
        Guidelines:
        - Key points: Main topics discussed, decisions made, important information shared
        - Action items: Specific tasks that need to be done, with who should do them and by when if mentioned
        - Future points: Topics that were deferred or should be discussed in a follow-up meeting
        - Management draft: Professional tone, highlight achievements and progress, mention blockers or risks
        
        If the transcription is unclear or doesn't contain meeting content, still provide your best interpretation.
        """
    }
    
    /// Send request to Gemini API
    private func sendRequest(prompt: String, apiKey: String) async throws -> GeminiResponse {
        guard let url = URL(string: "\(baseURL)?key=\(apiKey)") else {
            throw GeminiError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "contents": [
                [
                    "parts": [
                        ["text": prompt]
                    ]
                ]
            ],
            "generationConfig": [
                "temperature": 0.3,
                "maxOutputTokens": 2048,
                "responseMimeType": "application/json"
            ]
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw GeminiError.invalidResponse
        }
        
        if httpResponse.statusCode != 200 {
            // Try to parse error message
            if let errorResponse = try? JSONDecoder().decode(GeminiResponse.self, from: data),
               let errorMessage = errorResponse.error?.message {
                throw GeminiError.apiError(errorMessage)
            }
            throw GeminiError.httpError(httpResponse.statusCode)
        }
        
        return try JSONDecoder().decode(GeminiResponse.self, from: data)
    }
    
    /// Parse the JSON response into a MeetingSummary
    private func parseResponse(_ text: String) throws -> MeetingSummary {
        // Clean up the response (remove markdown code blocks if present)
        var cleanedText = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanedText.hasPrefix("```json") {
            cleanedText = String(cleanedText.dropFirst(7))
        }
        if cleanedText.hasPrefix("```") {
            cleanedText = String(cleanedText.dropFirst(3))
        }
        if cleanedText.hasSuffix("```") {
            cleanedText = String(cleanedText.dropLast(3))
        }
        cleanedText = cleanedText.trimmingCharacters(in: .whitespacesAndNewlines)
        
        guard let data = cleanedText.data(using: .utf8) else {
            throw GeminiError.parseError
        }
        
        do {
            let parsed = try JSONDecoder().decode(ParsedSummary.self, from: data)
            return parsed.toMeetingSummary()
        } catch {
            print("Failed to parse Gemini response: \(error)")
            print("Response text: \(cleanedText)")
            throw GeminiError.parseError
        }
    }
    
    /// Get API key from UserDefaults
    private func getAPIKey() -> String? {
        UserDefaults.standard.string(forKey: "gemini_api_key")
    }
    
    /// Save API key to UserDefaults
    static func saveAPIKey(_ key: String) {
        UserDefaults.standard.set(key, forKey: "gemini_api_key")
    }
    
    /// Check if API key is configured
    static var hasAPIKey: Bool {
        guard let key = UserDefaults.standard.string(forKey: "gemini_api_key") else {
            return false
        }
        return !key.isEmpty
    }
    
    /// Clear the stored API key
    static func clearAPIKey() {
        UserDefaults.standard.removeObject(forKey: "gemini_api_key")
    }
}

/// Errors that can occur with the Gemini service
enum GeminiError: LocalizedError {
    case noAPIKey
    case invalidURL
    case invalidResponse
    case httpError(Int)
    case apiError(String)
    case noResponse
    case parseError
    
    var errorDescription: String? {
        switch self {
        case .noAPIKey:
            return "No Gemini API key configured. Please add your API key in Settings."
        case .invalidURL:
            return "Invalid API URL."
        case .invalidResponse:
            return "Invalid response from server."
        case .httpError(let code):
            return "Server error (HTTP \(code))."
        case .apiError(let message):
            return "API error: \(message)"
        case .noResponse:
            return "No response from AI."
        case .parseError:
            return "Failed to parse AI response. Please try again."
        }
    }
}
