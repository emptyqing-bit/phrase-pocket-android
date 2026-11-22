package com.phrasepocket.app.util

import android.content.Context
import android.net.Uri
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.data.EntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.withTransaction
import com.phrasepocket.app.data.AppDatabase
import java.text.Normalizer
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
                    put("review", e.review)
                    put("reviewedAt", e.reviewedAt)
                    put("text", e.text)
                    put("meaning", e.meaning)
                    put("createdAt", iso(e.createdAt))
                    put("nextReviewAt", e.nextReviewAt)
                    put("reviewCount", e.reviewCount)
                    put("intervalDays", e.intervalDays)
                    put("easeFactor", e.easeFactor.toDouble())
                })
            }
            val backup = JSONObject().put("format", "phrase-pocket").put("version", 1).put("entries", array)
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { out ->
                out.write(backup.toString(2).toByteArray())
            }
            entries.size
        }

    /** Merges a backup, skipping entries whose (type, text) already exists. */
    suspend fun restore(context: Context, db: AppDatabase, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val raw = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().decodeToString()
            } ?: error("Cannot open backup")
            val parsed = when (val root = org.json.JSONTokener(raw).nextValue()) {
                is JSONArray -> root
                is JSONObject -> {
                    require(root.optString("format") == "phrase-pocket" && root.getInt("version") == 1)
                    root.getJSONArray("entries")
                }
                else -> error("Invalid backup")
            }
            val array = if (parsed.length() > 0 && parsed.opt(0) is JSONArray) {
                parsed.getJSONArray(0)
            } else parsed
            db.withTransaction { merge(db.entryDao(), array) }
        }

    private fun key(type: String, text: String): Pair<String, String> {
        if (type == Entry.TYPE_NOTE) return type to text.trim()
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .replace('’', '\'').replace('‘', '\'').trim().trimEnd('.', '!', '?', '…')
            .trim().replace(Regex("\\s+"), " ")
        return type to normalized
    }

    private suspend fun merge(dao: EntryDao, array: JSONArray): Int {
        val existing = dao.allEntries().mapTo(mutableSetOf()) { key(it.type, it.text) }
        var inserted = 0
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            // Accept both Android (`type`) and extension (`kind`) backups.
            val type = o.optString("kind").ifBlank { o.optString("type", Entry.TYPE_WORD) }
            val text = o.optString("text").trim()
            if (text.isEmpty() || (type != Entry.TYPE_WORD && type != Entry.TYPE_NOTE)) continue
            if (!existing.add(key(type, text))) continue
            dao.insert(
                Entry(
                    type = type,
                    review = o.optString("review").takeIf { it == "known" || it == "again" },
                    reviewedAt = o.optString("reviewedAt").takeUnless { it.isBlank() || it == "null" },
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
        return inserted
    }
}
