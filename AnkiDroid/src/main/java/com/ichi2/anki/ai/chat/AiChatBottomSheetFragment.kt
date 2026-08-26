// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiError
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.AiStreamingClient
import com.ichi2.anki.ai.AnthropicProvider
import com.ichi2.anki.ai.GeminiProvider
import com.ichi2.anki.ai.OpenAiProvider
import com.ichi2.anki.databinding.FragmentAiChatBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import com.ichi2.anki.utils.ext.behavior
import com.ichi2.anki.utils.ext.collectIn
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import dev.androidbroadcast.vbpd.viewBinding

class AiChatBottomSheetFragment : BottomSheetDialogFragment(R.layout.fragment_ai_chat) {
    private val binding by viewBinding(FragmentAiChatBinding::bind)
    private val adapter = AiChatMessageAdapter()

    private val viewModel: AiChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val args =
                    requireNotNull(BundleCompat.getParcelable(requireArguments(), ARG_LAUNCH_ARGS, AiChatLaunchArgs::class.java))
                val keyStore = AiKeyStore(requireContext())
                val providerKind = Prefs.aiProviderKind
                val provider =
                    when (providerKind) {
                        AiProviderKind.OPENAI -> OpenAiProvider()
                        AiProviderKind.ANTHROPIC -> AnthropicProvider()
                        AiProviderKind.GEMINI -> GeminiProvider()
                    }
                @Suppress("UNCHECKED_CAST")
                return AiChatViewModel(
                    noteId = args.noteId,
                    cardContent = args.cardContent,
                    provider = provider,
                    apiKey = keyStore.apiKey.orEmpty(),
                    model = Prefs.aiModelOverride?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
                    streamingClient = AiStreamingClient(),
                    storeMessage = { message -> MetaDB.storeAiChatMessage(requireContext(), args.noteId, message) },
                    loadHistory = { MetaDB.getAiChatMessages(requireContext(), args.noteId) },
                    onClearHistory = { MetaDB.deleteAiChatMessages(requireContext(), args.noteId) },
                ) as T
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        binding.messageList.layoutManager = LinearLayoutManager(requireContext())
        binding.messageList.adapter = adapter

        viewModel.messages.collectIn(viewLifecycleOwner.lifecycleScope) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) binding.messageList.scrollToPosition(messages.size - 1)
        }

        viewModel.errorFlow.collectIn(viewLifecycleOwner.lifecycleScope) { error ->
            showSnackbar(errorMessage(error))
        }

        viewModel.isStreaming.collectIn(viewLifecycleOwner.lifecycleScope) { isStreaming ->
            binding.sendButton.isEnabled = !isStreaming
            binding.messageInput.isEnabled = !isStreaming
            binding.clearHistoryButton.isEnabled = !isStreaming
            binding.generatingIndicator.isVisible = isStreaming
            // Prevent an accidental swipe-down/back-press/outside-tap from dropping an in-flight
            // request during the network delay before the first token arrives.
            isCancelable = !isStreaming
            behavior.isDraggable = !isStreaming
        }

        binding.sendButton.setOnClickListener {
            val text =
                binding.messageInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            val keyStore = AiKeyStore(requireContext())
            if (!keyStore.hasApiKey()) {
                showSnackbar(R.string.ai_chat_missing_api_key)
                return@setOnClickListener
            }
            viewModel.sendMessage(text)
            binding.messageInput.text?.clear()
        }

        binding.clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext()).show {
                title(R.string.ai_chat_clear_history)
                message(R.string.ai_chat_clear_history_confirm)
                positiveButton(R.string.ai_chat_clear_history) {
                    viewModel.clearHistory()
                    showSnackbar(R.string.ai_chat_history_cleared)
                }
                negativeButton(R.string.dialog_cancel)
            }
        }
    }

    private fun errorMessage(error: AiError): CharSequence =
        when (error) {
            is AiError.RateLimited ->
                if (error.retryAfterSeconds != null) {
                    getString(R.string.ai_chat_error_rate_limited_with_wait, error.retryAfterSeconds)
                } else {
                    getString(R.string.ai_chat_error_rate_limited)
                }
            is AiError.Http ->
                when (error.code) {
                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> getString(R.string.ai_chat_error_invalid_key)
                    in 500..599 -> getString(R.string.ai_chat_error_server)
                    else -> getString(R.string.ai_chat_error)
                }
            is AiError.Network -> getString(R.string.ai_chat_error_network)
            AiError.MissingApiKey -> getString(R.string.ai_chat_missing_api_key)
        }

    companion object {
        private const val TAG = "AiChatBottomSheetFragment"
        private const val ARG_LAUNCH_ARGS = "launchArgs"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        fun newInstance(args: AiChatLaunchArgs): AiChatBottomSheetFragment =
            AiChatBottomSheetFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_LAUNCH_ARGS, args) }
            }

        fun show(
            manager: FragmentManager,
            args: AiChatLaunchArgs,
        ) = newInstance(args).show(manager, TAG)
    }
}
