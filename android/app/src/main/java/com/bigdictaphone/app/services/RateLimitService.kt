package com.bigdictaphone.app.services

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service for managing Gemini API rate limits and request queuing.
 * 
 * Free tier limits for gemini-2.5-flash:
 * - 5 RPM (requests per minute)
 * - 250K TPM (tokens per minute)
 * - 20 RPD (requests per day)
 */
class RateLimitService(private val context: Context) {
    
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    
    // Rate limit constants (based on user's dashboard for gemini-2.5-flash)
    companion object {
        const val REQUESTS_PER_MINUTE = 5
        const val REQUESTS_PER_DAY = 20
        const val TOKENS_PER_MINUTE = 250_000
        
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 86_400_000L
    }
    
    // In-memory tracking
    private val requestTimestamps = mutableListOf<Long>()
    private var dailyRequestCount = 0
    private var dailyResetTime = 0L
    
    // Request queue
    private val requestQueue = ConcurrentLinkedQueue<QueuedRequest>()
    
    /**
     * Check if we can make an API request right now
     */
    suspend fun canMakeRequest(): RateLimitStatus = mutex.withLock {
        cleanupOldTimestamps()
        updateDailyTracking()
        
        val requestsInLastMinute = requestTimestamps.count { 
            System.currentTimeMillis() - it < MINUTE_MS 
        }
        
        return when {
            dailyRequestCount >= REQUESTS_PER_DAY -> {
                val resetIn = dailyResetTime - System.currentTimeMillis()
                RateLimitStatus.DailyLimitReached(resetIn)
            }
            requestsInLastMinute >= REQUESTS_PER_MINUTE -> {
                val oldestInWindow = requestTimestamps.filter { 
                    System.currentTimeMillis() - it < MINUTE_MS 
                }.minOrNull() ?: System.currentTimeMillis()
                val resetIn = MINUTE_MS - (System.currentTimeMillis() - oldestInWindow)
                RateLimitStatus.MinuteLimitReached(resetIn)
            }
            else -> {
                RateLimitStatus.Available(
                    remainingPerMinute = REQUESTS_PER_MINUTE - requestsInLastMinute,
                    remainingPerDay = REQUESTS_PER_DAY - dailyRequestCount
                )
            }
        }
    }
    
    /**
     * Record that a request was made
     */
    suspend fun recordRequest() = mutex.withLock {
        val now = System.currentTimeMillis()
        requestTimestamps.add(now)
        dailyRequestCount++
        
        // Keep only timestamps from the last minute for efficiency
        cleanupOldTimestamps()
    }
    
    /**
     * Get wait time until next available slot (in milliseconds)
     */
    suspend fun getWaitTime(): Long {
        return when (val status = canMakeRequest()) {
            is RateLimitStatus.Available -> 0L
            is RateLimitStatus.MinuteLimitReached -> status.resetInMs
            is RateLimitStatus.DailyLimitReached -> status.resetInMs
        }
    }
    
    /**
     * Add a request to the queue
     */
    fun queueRequest(request: QueuedRequest) {
        requestQueue.add(request)
    }
    
    /**
     * Get next queued request (if any)
     */
    fun getNextQueuedRequest(): QueuedRequest? {
        return requestQueue.poll()
    }
    
    /**
     * Get queue size
     */
    fun getQueueSize(): Int = requestQueue.size
    
    /**
     * Check if queue has items
     */
    fun hasQueuedRequests(): Boolean = requestQueue.isNotEmpty()
    
    /**
     * Clean up timestamps older than 1 minute
     */
    private fun cleanupOldTimestamps() {
        val cutoff = System.currentTimeMillis() - MINUTE_MS
        requestTimestamps.removeAll { it < cutoff }
    }
    
    /**
     * Update daily tracking (reset if new day)
     */
    private fun updateDailyTracking() {
        val now = System.currentTimeMillis()
        if (dailyResetTime == 0L || now >= dailyResetTime) {
            // Reset daily counter at midnight
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            dailyResetTime = calendar.timeInMillis
            dailyRequestCount = 0
        }
    }
    
    /**
     * Get current rate limit status as a displayable string
     */
    suspend fun getStatusString(): String {
        return when (val status = canMakeRequest()) {
            is RateLimitStatus.Available -> 
                "✅ ${status.remainingPerMinute}/min • ${status.remainingPerDay}/day"
            is RateLimitStatus.MinuteLimitReached -> 
                "⏳ Retry in ${status.resetInMs / 1000}s"
            is RateLimitStatus.DailyLimitReached -> 
                "🚫 Daily limit reached"
        }
    }
}

/**
 * Rate limit status
 */
sealed class RateLimitStatus {
    data class Available(
        val remainingPerMinute: Int,
        val remainingPerDay: Int
    ) : RateLimitStatus()
    
    data class MinuteLimitReached(val resetInMs: Long) : RateLimitStatus()
    data class DailyLimitReached(val resetInMs: Long) : RateLimitStatus()
}

/**
 * Request in the queue
 */
@Serializable
data class QueuedRequest(
    val id: String = UUID.randomUUID().toString(),
    val recordingId: String,
    val timestamp: Long = System.currentTimeMillis()
)
