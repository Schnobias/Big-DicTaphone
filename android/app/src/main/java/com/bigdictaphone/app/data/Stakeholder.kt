package com.bigdictaphone.app.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a stakeholder who receives management updates
 */
@Serializable
data class Stakeholder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val email: String,
    val role: String? = null  // e.g., "Manager", "Team Lead", "VP"
)
