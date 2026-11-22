package com.phrasepocket.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phrasepocket.app.R
import com.phrasepocket.app.data.AppDatabase
import com.phrasepocket.app.data.DictionaryHelper
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.widget.ReviewWidgetProvider
import kotlinx.coroutines.launch

/** Receives shared text (e.g. subtitles selected in a video app) and saves it. */
class ShareSaveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isEmpty()) {
            finish()
            return
        }

        val db = AppDatabase.get(this)
        val dialog = EntryEditDialog.show(
            this, lifecycleScope, null, Entry.TYPE_WORD, DictionaryHelper(this),
            showTypeSelector = true, initialText = shared
        ) { entry ->
            lifecycleScope.launch {
                db.entryDao().insert(entry)
                ReviewWidgetProvider.refreshAll(this@ShareSaveActivity)
                Toast.makeText(this@ShareSaveActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        dialog.setOnDismissListener {
            if (!isFinishing) finish()
        }
    }
}
