package com.phrasepocket.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query("SELECT * FROM entries WHERE type = :type ORDER BY createdAt DESC")
    fun entriesByType(type: String): Flow<List<Entry>>

    @Query(
        "SELECT * FROM entries WHERE type = :type AND (text LIKE '%' || :query || '%' " +
            "OR meaning LIKE '%' || :query || '%') ORDER BY createdAt DESC"
    )
    fun search(type: String, query: String): Flow<List<Entry>>

    @Insert
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT * FROM entries ORDER BY RANDOM() LIMIT 1")
    fun randomEntry(): Entry?

    @Query("SELECT * FROM entries WHERE type = 'word' AND meaning != '' ORDER BY text ASC")
    fun wordsWithMeaning(): List<Entry>

    @Query("SELECT * FROM entries WHERE type = 'word' AND nextReviewAt <= :now ORDER BY nextReviewAt ASC")
    fun dueWords(now: Long): List<Entry>

    @Query("SELECT * FROM entries")
    fun allEntries(): List<Entry>

    @Query("SELECT COUNT(*) FROM entries WHERE type = :type AND text = :text")
    suspend fun countByTypeAndText(type: String, text: String): Int
}
