// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.chat

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.CommonString
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiError
import com.ichi2.anki.ai.AiFailureKind
import com.ichi2.anki.ai.AiKeyStore
import com.ichi2.anki.ai.AiStreamingClient
import com.ichi2.anki.ai.AnthropicProvider
import com.ichi2.anki.ai.GeminiProvider
import com.ichi2.anki.ai.OpenAiProvider
import com.ichi2.anki.ai.kind
import com.ichi2.anki.ai.providerMessage
import com.ichi2.anki.databinding.FragmentAiChatBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AiProviderKind
import com.ichi2.anki.snackbar.setMaxLines
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
                    apiKey = keyStore.getApiKey(providerKind).orEmpty(),
                    model = Prefs.getAiModelOverride(providerKind)?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
                    streamingClient = AiStreamingClient(),
                    storeMessage = { message -> MetaDB.storeAiChatMessage(requireContext(), args.noteId, message) },
                    loadHistory = { MetaDB.getAiChatMessages(requireContext(), args.noteId) },
                    onClearHistory = { MetaDB.deleteAiChatMessages(requireContext(), args.noteId) },
                    onModelUnavailable = { Prefs.setAiModelOverride(providerKind, null) },
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
            val message = errorMessage(error)
            binding.retryReason.text = message
            val details = technicalDetails(error)
            showSnackbar(message) {
                setMaxLines(4)
                if (details != null) setAction(R.string.ai_chat_error_details) { showErrorDetails(message, details) }
            }
        }

        viewModel.canRetry.collectIn(viewLifecycleOwner.lifecycleScope) { canRetry ->
            binding.retryBar.isVisible = canRetry
            if (!canRetry) binding.retryReason.setText(R.string.ai_chat_no_reply)
        }

        viewModel.modelFallbackFlow.collectIn(viewLifecycleOwner.lifecycleScope) { fallback ->
            showSnackbar(getString(R.string.ai_chat_model_unavailable, fallback.unavailable, fallback.fallback))
        }

        viewModel.isStreaming.collectIn(viewLifecycleOwner.lifecycleScope) { isStreaming ->
            binding.sendButton.isEnabled = !isStreaming
            binding.messageInput.isEnabled = !isStreaming
            binding.clearHistoryButton.isEnabled = !isStreaming
            // Prevent an accidental swipe-down/back-press/outside-tap from dropping an in-flight
            // request during the network delay before the first token arrives.
            isCancelable = !isStreaming
            behavior.isDraggable = !isStreaming
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isAwaitingFirstToken.collectLatest { isAwaiting ->
                binding.generatingIndicator.isVisible = isAwaiting
                if (!isAwaiting) return@collectLatest
                val base = getString(R.string.ai_chat_generating)
                // Cycles "." → ".." → "..." until the first token arrives
                var dots = 0
                while (true) {
                    binding.generatingIndicator.text = base + ".".repeat(dots + 1)
                    dots = (dots + 1) % 3
                    delay(GENERATING_DOTS_INTERVAL_MILLIS)
                }
            }
        }

        binding.sendButton.setOnClickListener {
            val text =
                binding.messageInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            val keyStore = AiKeyStore(requireContext())
            if (!keyStore.hasApiKey(Prefs.aiProviderKind)) {
                showSnackbar(R.string.ai_chat_missing_api_key)
                return@setOnClickListener
            }
            viewModel.sendMessage(text)
            binding.messageInput.text?.clear()
        }

        binding.retryButton.setOnClickListener {
            if (!AiKeyStore(requireContext()).hasApiKey(Prefs.aiProviderKind)) {
                showSnackbar(R.string.ai_chat_missing_api_key)
                return@setOnClickListener
            }
            viewModel.retry()
        }

        binding.clearHistoryButton.setOnClickListener {
            AlertDialog.Builder(requireContext()).show {
                title(R.string.ai_chat_clear_history)
                message(R.string.ai_chat_clear_history_confirm)
                positiveButton(R.string.ai_chat_clear_history) {
                    viewModel.clearHistory()
                    showSnackbar(R.string.ai_chat_history_cleared)
                }
                negativeButton(CommonString.dialog_cancel)
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        setFragmentResult(REQUEST_KEY_DISMISSED, Bundle.EMPTY)
    }

    private fun errorMessage(error: AiError): CharSequence =
        when (error.kind) {
            AiFailureKind.RATE_LIMITED -> {
                val waitSeconds = (error as? AiError.RateLimited)?.retryAfterSeconds
                if (waitSeconds != null) {
                    getString(R.string.ai_chat_error_rate_limited_with_wait, waitSeconds)
                } else {
                    getString(R.string.ai_chat_error_rate_limited)
                }
            }
            AiFailureKind.MISSING_API_KEY -> getString(R.string.ai_chat_missing_api_key)
            AiFailureKind.INVALID_API_KEY -> getString(R.string.ai_chat_error_invalid_key)
            AiFailureKind.PERMISSION_DENIED -> getString(R.string.ai_chat_error_permission_denied)
            AiFailureKind.OUT_OF_CREDIT -> getString(R.string.ai_chat_error_out_of_credit)
            AiFailureKind.MODEL_NOT_FOUND -> getString(R.string.ai_chat_error_model_not_found)
            AiFailureKind.INPUT_TOO_LONG -> getString(R.string.ai_chat_error_input_too_long)
            AiFailureKind.BAD_REQUEST -> getString(R.string.ai_chat_error_bad_request)
            AiFailureKind.OVERLOADED -> getString(R.string.ai_chat_error_overloaded)
            AiFailureKind.SERVER_ERROR -> getString(R.string.ai_chat_error_server)
            AiFailureKind.TIMEOUT -> getString(R.string.ai_chat_error_timeout)
            AiFailureKind.NO_CONNECTION -> getString(R.string.ai_chat_error_no_connection)
            AiFailureKind.NETWORK -> getString(R.string.ai_chat_error_network)
            AiFailureKind.EMPTY_RESPONSE -> getString(R.string.ai_chat_error_empty_response)
            AiFailureKind.UNKNOWN -> getString(R.string.ai_chat_error)
        }

    /** What the provider or the network stack reported, for the "Details" dialog; `null` if nothing useful. */
    private fun technicalDetails(error: AiError): String? =
        when (error) {
            is AiError.Http -> listOfNotNull("HTTP ${error.code}", error.providerMessage).joinToString("\n")
            is AiError.RateLimited -> listOfNotNull("HTTP 429", error.providerMessage).joinToString("\n")
            is AiError.Network -> error.cause?.let { "${it.javaClass.simpleName}: ${it.message}" }
            AiError.MissingApiKey, AiError.EmptyResponse -> null
        }

    private fun showErrorDetails(
        message: CharSequence,
        details: String,
    ) {
        AlertDialog.Builder(requireContext()).show {
            title(R.string.ai_chat_error_details_title)
            message(text = "$message\n\n$details")
            positiveButton(CommonString.dialog_ok)
        }
    }

    companion object {
        private const val TAG = "AiChatBottomSheetFragment"

        /** Fragment result key sent when the sheet is dismissed. */
        const val REQUEST_KEY_DISMISSED = "aiChatDismissed"
        private const val ARG_LAUNCH_ARGS = "launchArgs"
        private const val GENERATING_DOTS_INTERVAL_MILLIS = 400L

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
