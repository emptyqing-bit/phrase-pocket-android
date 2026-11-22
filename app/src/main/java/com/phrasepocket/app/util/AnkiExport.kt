package com.phrasepocket.app.util

import android.content.Context
import android.net.Uri
import com.phrasepocket.app.data.EntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AnkiExport {

    /** Writes "text<TAB>meaning" lines for all saved words with a meaning. */
    suspend fun export(context: Context, dao: EntryDao, uri: Uri): Int =
        withContext(Dispatchers.IO) {
            val words = dao.wordsWithMeaning()
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.bufferedWriter().use { writer ->
                    words.forEach { entry ->
                        val text = entry.text.replace('\t', ' ').replace('\n', ' ')
                        val meaning = entry.meaning.replace('\t', ' ').replace('\n', ' ')
                        writer.write("$text\t$meaning\n")
                    }
                }
            }
            words.size
        }
}
