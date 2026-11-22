package com.phrasepocket.app.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.phrasepocket.app.R
import com.phrasepocket.app.data.DictionaryHelper
import com.phrasepocket.app.data.Entry
import com.phrasepocket.app.databinding.DialogEntryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object EntryEditDialog {

    fun show(
        context: Context,
        scope: CoroutineScope,
        existing: Entry?,
        type: String,
        dict: DictionaryHelper,
        showTypeSelector: Boolean,
        initialText: String = "",
        onSave: (Entry) -> Unit
    ): AlertDialog {
        val binding = DialogEntryBinding.inflate(LayoutInflater.from(context))
        binding.inputText.setText(existing?.text ?: initialText)
        binding.inputMeaning.setText(existing?.meaning.orEmpty())

        binding.typeGroup.visibility = if (showTypeSelector) View.VISIBLE else View.GONE
        binding.radioWord.isChecked = (existing?.type ?: type) == Entry.TYPE_WORD
        binding.radioNote.isChecked = (existing?.type ?: type) == Entry.TYPE_NOTE

        binding.buttonLookup.setOnClickListener {
            val text = binding.inputText.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            scope.launch {
                val meaning = dict.lookup(text)
                if (meaning != null) {
                    binding.inputMeaning.setText(meaning)
                } else {
                    Toast.makeText(context, R.string.lookup_missing, Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (com.phrasepocket.app.util.AiHelper.isConfigured(context)) {
            binding.buttonAi.visibility = View.VISIBLE
            binding.buttonAi.setOnClickListener {
                val text = binding.inputText.text.toString().trim()
                if (text.isEmpty()) return@setOnClickListener
                binding.buttonAi.isEnabled = false
                scope.launch {
                    try {
                        val result = com.phrasepocket.app.util.AiHelper.lookup(context, text)
                        binding.inputMeaning.setText(result)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.ai_failed, e.message ?: "unknown"),
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        binding.buttonAi.isEnabled = true
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.dialog_add else R.string.dialog_edit)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val text = binding.inputText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                val selectedType = when {
                    showTypeSelector && binding.radioNote.isChecked -> Entry.TYPE_NOTE
                    showTypeSelector -> Entry.TYPE_WORD
                    else -> existing?.type ?: type
                }
                onSave(
                    Entry(
                        id = existing?.id ?: 0,
                        type = selectedType,
                        text = text,
                        meaning = binding.inputMeaning.text.toString().trim(),
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        return dialog
    }
}
