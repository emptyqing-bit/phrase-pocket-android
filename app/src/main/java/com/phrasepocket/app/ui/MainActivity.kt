package com.phrasepocket.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.phrasepocket.app.R
import com.phrasepocket.app.data.AppDatabase
import com.phrasepocket.app.data.DictionaryHelper
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.databinding.ActivityMainBinding
import com.phrasepocket.app.floating.FloatingService
import com.phrasepocket.app.util.AnkiExport
import com.phrasepocket.app.widget.ReviewWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dict: DictionaryHelper
    private lateinit var tts: com.phrasepocket.app.util.TtsHelper
    private val db by lazy { AppDatabase.get(this) }
    private var currentType = Entry.TYPE_WORD
    private var observeJob: Job? = null

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val ok = dict.importDatabase(uri)
                Toast.makeText(
                    this@MainActivity,
                    if (ok) R.string.dict_import_ok else R.string.dict_import_fail,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val overlayLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                startFloating()
            } else {
                Toast.makeText(this, R.string.floating_permission_needed, Toast.LENGTH_LONG).show()
            }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val count = AnkiExport.export(this@MainActivity, db.entryDao(), uri)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.export_done, count),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val count = com.phrasepocket.app.util.BackupManager.export(this@MainActivity, db.entryDao(), uri)
                Toast.makeText(this@MainActivity, getString(R.string.backup_done, count), Toast.LENGTH_LONG).show()
            }
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            lifecycleScope.launch {
                val count = try {
                    com.phrasepocket.app.util.BackupManager.restore(this@MainActivity, db, uri)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (error: Exception) {
                    Toast.makeText(this@MainActivity, R.string.restore_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                ReviewWidgetProvider.refreshAll(this@MainActivity)
                Toast.makeText(this@MainActivity, getString(R.string.restore_done, count), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        dict = DictionaryHelper(this)
        tts = com.phrasepocket.app.util.TtsHelper(this)

        val adapter = EntriesAdapter(
            onClick = { entry -> openEditor(entry) },
            onLongClick = { entry -> confirmDelete(entry) },
            onSpeak = { entry -> tts.speak(entry.text) }
        )
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_words))
        binding.tabs.addTab(binding.tabs.newTab().setText(R.string.tab_notes))
        binding.tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                currentType = if (tab.position == 0) Entry.TYPE_WORD else Entry.TYPE_NOTE
                observe()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })

        binding.search.addTextChangedListener { observe() }

        binding.fab.setOnClickListener {
            EntryEditDialog.show(
                this, lifecycleScope, null, currentType, dict, showTypeSelector = false
            ) { entry ->
                lifecycleScope.launch {
                    db.entryDao().insert(entry)
                    ReviewWidgetProvider.refreshAll(this@MainActivity)
                }
            }
        }

        observe()
    }

    private fun observe() {
        observeJob?.cancel()
        val query = binding.search.text?.toString()?.trim().orEmpty()
        val flow = if (query.isEmpty()) {
            db.entryDao().entriesByType(currentType)
        } else {
            db.entryDao().search(currentType, query)
        }
        observeJob = lifecycleScope.launch {
            flow.collectLatest { entries ->
                (binding.list.adapter as EntriesAdapter).submitList(entries)
                binding.empty.visibility =
                    if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun openEditor(entry: Entry) {
        EntryEditDialog.show(
            this, lifecycleScope, entry, entry.type, dict, showTypeSelector = false
        ) { updated ->
            lifecycleScope.launch {
                db.entryDao().editContent(entry.id, updated.type, updated.text, updated.meaning)
                ReviewWidgetProvider.refreshAll(this@MainActivity)
            }
        }
    }

    private fun confirmDelete(entry: Entry) {
        AlertDialog.Builder(this)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    db.entryDao().delete(entry)
                    ReviewWidgetProvider.refreshAll(this@MainActivity)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_floating -> {
            if (Settings.canDrawOverlays(this)) {
                startFloating()
            } else {
                overlayLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            true
        }
        R.id.action_review -> {
            startActivity(Intent(this, ReviewActivity::class.java))
            true
        }
        R.id.action_backup -> {
            backupLauncher.launch("phrase-pocket-backup.json")
            true
        }
        R.id.action_restore -> {
            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            true
        }
        R.id.action_ai_settings -> {
            showAiSettings()
            true
        }
        R.id.action_import_dict -> {
            importLauncher.launch(arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
            true
        }
        R.id.action_export_anki -> {
            exportLauncher.launch("phrase-pocket-anki.tsv")
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun startFloating() {
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
    }

    private fun showAiSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_ai_settings, null)
        val baseUrl = view.findViewById<android.widget.EditText>(R.id.ai_base_url)
        val apiKey = view.findViewById<android.widget.EditText>(R.id.ai_api_key)
        val model = view.findViewById<android.widget.EditText>(R.id.ai_model)
        val (url, key, modelName) = com.phrasepocket.app.util.AiHelper.config(this)
        baseUrl.setText(url)
        apiKey.setText(key)
        model.setText(modelName)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_ai_settings)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                com.phrasepocket.app.util.AiHelper.saveConfig(
                    this,
                    baseUrl.text.toString(),
                    apiKey.text.toString(),
                    model.text.toString()
                )
                Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}
