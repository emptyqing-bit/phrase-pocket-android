package com.phrasepocket.app.data

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline dictionary with two sources:
 * 1. A small built-in word list bundled in assets/dict.tsv (sample entries).
 * 2. A full ECDICT sqlite database imported by the user (table `stardict`,
 *    columns `word` / `translation`), copied to internal storage.
 */
class DictionaryHelper(private val context: Context) {

    private var builtin: Map<String, String>? = null

    private fun builtinMap(): Map<String, String> {
        builtin?.let { return it }
        val map = mutableMapOf<String, String>()
        runCatching {
            context.assets.open("dict.tsv").bufferedReader().forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size >= 2) map[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }
        builtin = map
        return map
    }

    private fun importedDbFile(): File = File(context.filesDir, "ecdict.db")

    fun hasImportedDatabase(): Boolean = importedDbFile().exists()

    suspend fun importDatabase(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val staged = File.createTempFile("dictionary-", ".db", context.filesDir)
            try {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    staged.outputStream().use { output -> input.copyTo(output) }
                }
                SQLiteDatabase.openDatabase(staged.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT word, translation FROM stardict LIMIT 1", null).use {
                        require(it.moveToFirst()) { "Empty dictionary" }
                    }
                }
                android.system.Os.rename(staged.absolutePath, importedDbFile().absolutePath)
                true
            } finally {
                staged.delete()
            }
        }.getOrDefault(false)
    }

    suspend fun lookup(text: String): String? = withContext(Dispatchers.IO) {
        val key = text.trim()
        if (key.isEmpty()) return@withContext null
        if (hasImportedDatabase()) {
            runCatching {
                SQLiteDatabase.openDatabase(
                    importedDbFile().absolutePath, null, SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.rawQuery(
                        "SELECT translation FROM stardict WHERE word = ? COLLATE NOCASE LIMIT 1",
                        arrayOf(key)
                    ).use { cursor ->
                        if (cursor.moveToFirst()) {
                            val value = cursor.getString(0)
                            if (!value.isNullOrBlank()) return@withContext value
                        }
                    }
                }
            }
        }
        builtinMap()[key.lowercase()]
    }
}
