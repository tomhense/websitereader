package com.example.websitereader.settings

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.websitereader.R
import com.example.websitereader.databinding.FragmentSettingsBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private val defaultName = "OpenAI"
    private val defaultApiBaseUrl = "https://api.openai.com"
    private val defaultPricePer1M = 12 / 4
    private val defaultMaxChunk = 4096
    private val defaultVoices = listOf(
        "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer"
    )
    private val defaultModelName = "gpt-4o-mini-tts"
    private val defaultAudioFormat = "mp3"
    private val defaultAsyncSynthesization = false

    // Added a default voice name; assuming "alloy" as default
    private val defaultVoiceName = "alloy"

    private lateinit var entries: MutableList<TTSProviderEntry>
    private lateinit var adapter: TTSProviderEntryAdapter
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentSettingsBinding.bind(view)

        val window = requireActivity().window
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )

        entries = TTSProviderEntryStorage.load(requireContext()).toMutableList()

        adapter = TTSProviderEntryAdapter(entries) { position, entry ->
            showEntryDialog(editIndex = position, entry = entry)
        }

        binding.entryRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.entryRecyclerView.adapter = adapter

        binding.addEntryButton.setOnClickListener { showEntryDialog() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showEntryDialog(editIndex: Int? = null, entry: TTSProviderEntry? = null) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_tts_provider, null)

        val editName = dialogView.findViewById<TextInputEditText>(R.id.editName)
        val editApiBaseUrl = dialogView.findViewById<TextInputEditText>(R.id.editApiBaseUrl)
        val editVoiceName =
            dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.editVoiceName)
        val editPricePer1M = dialogView.findViewById<TextInputEditText>(R.id.editPricePer1M)
        val editMaxChunkLength = dialogView.findViewById<TextInputEditText>(R.id.editMaxChunkLength)
        val editApiKey = dialogView.findViewById<TextInputEditText>(R.id.editApiKey)
        val editModelName = dialogView.findViewById<TextInputEditText>(R.id.editModelName)
        val audioFormat = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.selectAudioFormat)
        val asyncSynthesization = dialogView.findViewById<MaterialCheckBox>(R.id.checkBoxAsyncSynthesization)

        // Set autocomplete adapter for voice names on the dialog view's TextInputEditText
        editVoiceName.setAdapter(
            ArrayAdapter(
                requireContext(), android.R.layout.simple_list_item_1, defaultVoices
            )
        )

        editVoiceName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) editVoiceName.showDropDown()
        }
        editVoiceName.setOnClickListener {
            editVoiceName.showDropDown()
        }

        if (entry != null) {
            // Editing: prefill fields with current entry data
            editName.setText(entry.name)
            editApiBaseUrl.setText(entry.apiBaseUrl)
            editVoiceName.setText(entry.voiceName)
            editPricePer1M.setText(entry.pricePer1MCharacters.toString())
            editMaxChunkLength.setText(entry.maxChunkLength.toString())
            editApiKey.setText(entry.apiKey)
            editModelName.setText(entry.modelName)
            audioFormat.setText(entry.audioFormat)
            asyncSynthesization.isChecked = entry.asyncSynthesization
        } else {
            // Adding new: prefill with defaults
            editName.setText(defaultName)
            editApiBaseUrl.setText(defaultApiBaseUrl)
            editVoiceName.setText(defaultVoiceName)
            editPricePer1M.setText(defaultPricePer1M.toString())
            editMaxChunkLength.setText(defaultMaxChunk.toString())
            editApiKey.setText("")
            editModelName.setText(defaultModelName)
            audioFormat.setText(defaultAudioFormat)
            asyncSynthesization.isChecked = defaultAsyncSynthesization
        }

        MaterialAlertDialogBuilder(requireContext()).setTitle(if (editIndex != null) "Edit Provider" else "Add Provider")
            .setView(dialogView).setPositiveButton("Save") { dialog, _ ->
                val name = editName.text?.toString()?.trim().orEmpty()
                val apiBaseUrl = editApiBaseUrl.text?.toString()?.trim().orEmpty()
                val voiceName = editVoiceName.text?.toString()?.trim().orEmpty()
                val priceStr = editPricePer1M.text?.toString()?.trim().orEmpty()
                val maxChunkStr = editMaxChunkLength.text?.toString()?.trim().orEmpty()
                val apiKey = editApiKey.text?.toString()?.trim().orEmpty()
                val modelName = editModelName.text?.toString()?.trim().orEmpty()
                val price = priceStr.toDoubleOrNull()
                val maxChunk = maxChunkStr.toIntOrNull()
                val audioFormat = audioFormat.text?.toString()?.trim().orEmpty()
                val asyncSynthesization = asyncSynthesization.isChecked

                if (name.isEmpty() || apiBaseUrl.isEmpty() || voiceName.isEmpty() || price == null || maxChunk == null || audioFormat.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Please fill in all fields with valid values",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Keep dialog open by overriding the button's click listener after showing
                    // MaterialAlertDialogBuilder closes by default, so recreate dialog:
                    // So just do nothing here; it's fine that dialog closes
                    return@setPositiveButton
                }

                val newEntry = TTSProviderEntry(
                    name = name,
                    apiBaseUrl = apiBaseUrl,
                    voiceName = voiceName,
                    pricePer1MCharacters = price,
                    maxChunkLength = maxChunk,
                    apiKey = apiKey,
                    modelName = modelName,
                    audioFormat = audioFormat,
                    asyncSynthesization = asyncSynthesization
                )

                if (editIndex != null) {
                    entries[editIndex] = newEntry
                    adapter.notifyItemChanged(editIndex)
                } else {
                    entries.add(newEntry)
                    adapter.notifyItemInserted(entries.size - 1)
                }

                TTSProviderEntryStorage.save(requireContext(), entries)
            }.setNegativeButton("Cancel", null).show()
    }
}