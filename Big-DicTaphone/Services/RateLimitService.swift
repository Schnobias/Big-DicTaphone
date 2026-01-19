import Foundation

/// Service for managing Gemini API rate limits and request queuing.
/// Free tier limits for gemini-2.5-flash:
/// - 5 RPM (requests per minute)
/// - 20 RPD (requests per day)
actor RateLimitService {
    static let shared = RateLimitService()
    
    // Rate limit constants
    private let requestsPerMinute = 5
    private let requestsPerDay = 20
    private let minuteInterval: TimeInterval = 60
    
    // Storage keys
    private let timestampsKey = "api_request_timestamps"
    private let dailyCountKey = "api_daily_count"
    private let dailyResetKey = "api_daily_reset_time"
    
    private let defaults = UserDefaults.standard
    
    private init() {}
    
    enum RateLimitStatus {
        case available
        case minuteLimitReached(resetIn: TimeInterval)
        case dailyLimitReached
    }
    
    /// Check if we can make a request
    func canMakeRequest() -> RateLimitStatus {
        cleanupOldTimestamps()
        updateDailyTracking()
        
        let dailyCount = defaults.integer(forKey: dailyCountKey)
        if dailyCount >= requestsPerDay {
            return .dailyLimitReached
        }
        
        let timestamps = (defaults.array(forKey: timestampsKey) as? [TimeInterval]) ?? []
        if timestamps.count >= requestsPerMinute {
            if let oldest = timestamps.first {
                let now = Date().timeIntervalSince1970
                let resetIn = minuteInterval - (now - oldest)
                return .minuteLimitReached(resetIn: max(0, resetIn))
            }
        }
        
        return .available
    }
    
    /// Record a request
    func recordRequest() {
        let now = Date().timeIntervalSince1970
        
        // Update minute tracking
        var timestamps = (defaults.array(forKey: timestampsKey) as? [TimeInterval]) ?? []
        timestamps.append(now)
        defaults.set(timestamps, forKey: timestampsKey)
        
        // Update daily tracking
        let dailyCount = defaults.integer(forKey: dailyCountKey)
        defaults.set(dailyCount + 1, forKey: dailyCountKey)
        
        cleanupOldTimestamps()
    }
    
    /// Clean up timestamps older than 1 minute
    private func cleanupOldTimestamps() {
        let now = Date().timeIntervalSince1970
        let cutoff = now - minuteInterval
        
        var timestamps = (defaults.array(forKey: timestampsKey) as? [TimeInterval]) ?? []
        timestamps = timestamps.filter { $0 >= cutoff }
        defaults.set(timestamps, forKey: timestampsKey)
    }
    
    /// Reset daily counter if a new day has started
    private func updateDailyTracking() {
        let now = Date().timeIntervalSince1970
        let resetTime = defaults.double(forKey: dailyResetKey)
        
        if resetTime == 0 || now >= resetTime {
            // Calculate next midnight
            var calendar = Calendar.current
            if let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date()),
               let midnight = calendar.date(bySettingHour: 0, minute: 0, second: 0, of: tomorrow) {
                
                defaults.set(midnight.timeIntervalSince1970, forKey: dailyResetKey)
                defaults.set(0, forKey: dailyCountKey)
            }
        }
    }
    
    /// Get status string for UI
    func getStatusString() -> String {
        let status = canMakeRequest()
        let timestamps = (defaults.array(forKey: timestampsKey) as? [TimeInterval]) ?? []
        let dailyCount = defaults.integer(forKey: dailyCountKey)
        
        switch status {
        case .available:
            return "✅ \(requestsPerMinute - timestamps.count)/min • \(requestsPerDay - dailyCount)/day"
        case .minuteLimitReached(let resetIn):
            return "⏳ Retry in \(Int(resetIn))s"
        case .dailyLimitReached:
            return "🚫 Daily limit reached"
        }
    }
}
