package com.phrasepocket.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.phrasepocket.app.R
import com.phrasepocket.app.data.AppDatabase
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.databinding.ActivityReviewBinding
import com.phrasepocket.app.util.SrsScheduler
import com.phrasepocket.app.util.TtsHelper
import com.phrasepocket.app.widget.ReviewWidgetProvider
import kotlinx.coroutines.launch

/** Flashcard review with SM-2 lite scheduling and TTS pronunciation. */
class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var tts: TtsHelper
    private val db by lazy { AppDatabase.get(this) }

    private var queue: List<Entry> = emptyList()
    private var index = 0
    private var revealed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        tts = TtsHelper(this)

        binding.buttonShow.setOnClickListener { reveal() }
        binding.buttonSpeak.setOnClickListener {
            current()?.let { e -> tts.speak(e.text) }
        }
        binding.buttonAgain.setOnClickListener { grade(0) }
        binding.buttonHard.setOnClickListener { grade(1) }
        binding.buttonGood.setOnClickListener { grade(3) }

        lifecycleScope.launch {
            queue = db.entryDao().dueWords(System.currentTimeMillis())
            if (queue.isEmpty()) showDone() else showCurrent()
        }
    }

    private fun current(): Entry? = queue.getOrNull(index)

    private fun showCurrent() {
        val entry = current() ?: return showDone()
        revealed = false
        binding.reviewProgress.text = getString(R.string.review_progress, index + 1, queue.size)
        binding.reviewText.text = entry.text
        binding.reviewMeaning.text = entry.meaning
        binding.reviewMeaning.visibility = View.INVISIBLE
        binding.buttonShow.visibility = View.VISIBLE
        binding.gradeGroup.visibility = View.INVISIBLE
    }

    private fun reveal() {
        revealed = true
        binding.reviewMeaning.visibility = View.VISIBLE
        binding.buttonShow.visibility = View.GONE
        binding.gradeGroup.visibility = View.VISIBLE
    }

    private fun grade(quality: Int) {
        val entry = current() ?: return
        val updated = SrsScheduler.schedule(entry, quality)
        lifecycleScope.launch {
            db.entryDao().update(updated)
            ReviewWidgetProvider.refreshAll(this@ReviewActivity)
            index++
            if (index >= queue.size) showDone() else showCurrent()
        }
    }

    private fun showDone() {
        binding.reviewProgress.text = ""
        binding.reviewText.text = getString(R.string.review_done)
        binding.reviewMeaning.text = getString(R.string.review_done_hint, queue.size)
        binding.reviewMeaning.visibility = View.VISIBLE
        binding.buttonShow.visibility = View.GONE
        binding.gradeGroup.visibility = View.GONE
        binding.buttonSpeak.visibility = View.GONE
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
