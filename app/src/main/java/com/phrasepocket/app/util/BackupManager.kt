package com.phrasepocket.app.util

import android.content.Context
import android.net.Uri
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.data.EntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * JSON backup / restore of the whole collection. The format matches the
 * browser extension's backup (fields `kind` / `text` / `meaning`, ISO dates),
 * so backups can be merged across Android and desktop with deduplication.
 */
object BackupManager {

    private fun iso(time: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(time))

    private fun parseTime(value: Any?, fallback: Long): Long = when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(value.substringBefore('Z'))?.time
        }.getOrNull() ?: fallback
        else -> fallback
    }

    suspend fun export(context: Context, dao: EntryDao, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val entries = dao.allEntries()
            val array = JSONArray()
            entries.forEach { e ->
                array.put(JSONObject().apply {
                    put("kind", e.type)
                    put("text", e.text)
                    put("meaning", e.meaning)
                    put("createdAt", iso(e.createdAt))
                    put("nextReviewAt", e.nextReviewAt)
                    put("reviewCount", e.reviewCount)
                    put("intervalDays", e.intervalDays)
                    put("easeFactor", e.easeFactor.toDouble())
                })
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(array.toString(2).toByteArray())
            }
            entries.size
        }

    /** Merges a backup, skipping entries whose (type, text) already exists. */
    suspend fun import(context: Context, dao: EntryDao, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: return@withContext 0
            val parsed = JSONArray(raw)
            val array = if (parsed.length() > 0 && parsed.opt(0) is JSONArray) {
                parsed.getJSONArray(0)
            } else parsed
            var inserted = 0
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                // Accept both Android (`type`) and extension (`kind`) backups.
                val type = o.optString("kind").ifBlank { o.optString("type", Entry.TYPE_WORD) }
                val text = o.optString("text").trim()
                if (text.isEmpty() || (type != Entry.TYPE_WORD && type != Entry.TYPE_NOTE)) continue
                if (dao.countByTypeAndText(type, text) > 0) continue
                dao.insert(
                    Entry(
                        type = type,
                        text = text,
                        meaning = o.optString("meaning"),
                        createdAt = parseTime(o.opt("createdAt"), System.currentTimeMillis()),
                        nextReviewAt = parseTime(o.opt("nextReviewAt"), 0),
                        reviewCount = o.optInt("reviewCount"),
                        intervalDays = o.optInt("intervalDays"),
                        easeFactor = o.optDouble("easeFactor", 2.5).toFloat()
                    )
                )
                inserted++
            }
            inserted
        }
}
