package com.phrasepocket.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // TYPE_WORD or TYPE_NOTE
    val text: String,
    val meaning: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    // Spaced-repetition state (SM-2 lite); nextReviewAt = 0 means "due now".
    val nextReviewAt: Long = 0,
    val reviewCount: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Float = 2.5f,
    val review: String? = null,
    val reviewedAt: String? = null
) {
    companion object {
        const val TYPE_WORD = "word"
        const val TYPE_NOTE = "note"
    }
}
