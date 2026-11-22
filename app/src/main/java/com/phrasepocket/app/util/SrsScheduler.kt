package com.phrasepocket.app.util

import com.phrasepocket.app.data.Entry

/** SM-2 lite scheduling for the review mode. */
object SrsScheduler {

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * quality: 0 = 不认识 (again in 10 min), 1 = 模糊 (short interval), 3 = 认识 (SM-2 growth).
     * Returns an updated copy; the caller persists it.
     */
    fun schedule(entry: Entry, quality: Int, now: Long = System.currentTimeMillis()): Entry {
        var ease = entry.easeFactor
        var count = entry.reviewCount
        var interval = entry.intervalDays
        val next: Long
        when (quality) {
            0 -> {
                count = 0
                interval = 0
                ease = maxOf(1.3f, ease - 0.2f)
                next = now + 10 * 60 * 1000
            }
            1 -> {
                count += 1
                interval = if (interval < 1) 1 else interval
                next = now + interval * DAY_MS
            }
            else -> {
                interval = when (count) {
                    0 -> 1
                    1 -> 3
                    else -> Math.round(interval * ease).coerceAtLeast(1)
                }
                count += 1
                next = now + interval * DAY_MS
            }
        }
        return entry.copy(
            nextReviewAt = next,
            reviewCount = count,
            intervalDays = interval,
            easeFactor = ease
        )
    }
}
