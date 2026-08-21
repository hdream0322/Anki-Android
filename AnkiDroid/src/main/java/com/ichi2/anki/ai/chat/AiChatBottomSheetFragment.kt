package com.ichi2.anki.ai.chat

import android.os.Bundle
import android.view.View
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
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
import com.ichi2.anki.utils.ext.collectIn
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
                    model = provider.defaultModel,
                    streamingClient = AiStreamingClient(),
                    storeMessage = { message -> MetaDB.storeAiChatMessage(requireContext(), args.noteId, message) },
                    loadHistory = { MetaDB.getAiChatMessages(requireContext(), args.noteId) },
                ) as T
            }
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.messageList.layoutManager = LinearLayoutManager(requireContext())
        binding.messageList.adapter = adapter

        viewModel.messages.collectIn(viewLifecycleOwner.lifecycleScope) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) binding.messageList.scrollToPosition(messages.size - 1)
        }

        viewModel.errorFlow.collectIn(viewLifecycleOwner.lifecycleScope) {
            showSnackbar(R.string.ai_chat_error)
        }

        viewModel.isStreaming.collectIn(viewLifecycleOwner.lifecycleScope) { isStreaming ->
            binding.sendButton.isEnabled = !isStreaming
            binding.messageInput.isEnabled = !isStreaming
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
    }

    companion object {
        private const val TAG = "AiChatBottomSheetFragment"
        private const val ARG_LAUNCH_ARGS = "launchArgs"

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
