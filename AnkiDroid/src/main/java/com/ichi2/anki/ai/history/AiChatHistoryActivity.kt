// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ai.history

import android.graphics.Color
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type.displayCutout
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.MetaDB
import com.ichi2.anki.R
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.ai.aiChatSystemPrompt
import com.ichi2.anki.ai.chat.AiChatBottomSheetFragment
import com.ichi2.anki.backend.stripHTMLAndSpecialFields
import com.ichi2.anki.databinding.ActivityAiChatHistoryBinding
import com.ichi2.anki.databinding.ItemAiChatHistoryBinding
import com.ichi2.anki.launchCatchingTask
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.NoteId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.startup.ensureStorageIsReady
import com.ichi2.anki.ui.windows.reviewer.AiChatLaunchArgs
import com.ichi2.anki.utils.ext.collectIn
import dev.androidbroadcast.vbpd.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import timber.log.Timber

/** Lists every note the user has chatted with AI about; tapping one reopens that chat. */
class AiChatHistoryActivity : AnkiActivity(R.layout.activity_ai_chat_history) {
    private val binding by viewBinding(ActivityAiChatHistoryBinding::bind)

    private val viewModel: AiChatHistoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val context = applicationContext
                @Suppress("UNCHECKED_CAST")
                return AiChatHistoryViewModel(
                    loadSummaries = { withContext(Dispatchers.IO) { MetaDB.getAiChatSummaries(context) } },
                    resolveTitles = { nids -> withCol { noteTitles(nids) } },
                ) as T
            }
        }
    }

    private val adapter = HistoryAdapter { item -> openChat(item.nid) }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (showedActivityFailedScreen(savedInstanceState)) return
        super.onCreate(savedInstanceState)
        if (!ensureStorageIsReady()) return

        enableToolbar().setDisplayHomeAsUpEnabled(true)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
            val bars = insets.getInsets(systemBars() or displayCutout())
            binding.toolbarContainer.updatePadding(left = bars.left, right = bars.right, top = bars.top)
            binding.historyList.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            insets
        }

        binding.historyList.layoutManager = LinearLayoutManager(this)
        binding.historyList.adapter = adapter

        viewModel.items.collectIn(lifecycleScope) { items ->
            if (items == null) return@collectIn
            adapter.submitList(items)
            binding.emptyText.isVisible = items.isEmpty()
        }

        // The chat may have added messages or cleared its history
        supportFragmentManager.setFragmentResultListener(AiChatBottomSheetFragment.REQUEST_KEY_DISMISSED, this) { _, _ ->
            viewModel.refresh()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun openChat(nid: NoteId) {
        Timber.i("Opening AI chat from history")
        launchCatchingTask {
            val content =
                withCol {
                    val card =
                        try {
                            getNote(nid).cards(this).firstOrNull()
                        } catch (e: BackendNotFoundException) {
                            null
                        } ?: return@withCol null
                    aiChatSystemPrompt(card)
                }
            if (content == null) {
                showSnackbar(R.string.ai_chat_history_note_missing)
                viewModel.refresh()
                return@launchCatchingTask
            }
            // The answer may already be known: send it so the AI explains instead of only hinting
            AiChatBottomSheetFragment.show(supportFragmentManager, AiChatLaunchArgs(nid, content, isAnswerShown = true))
        }
    }

    private class HistoryAdapter(
        private val onClick: (AiChatHistoryItem) -> Unit,
    ) : ListAdapter<AiChatHistoryItem, HistoryAdapter.Holder>(DIFF) {
        class Holder(
            val binding: ItemAiChatHistoryBinding,
        ) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = Holder(ItemAiChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(
            holder: Holder,
            position: Int,
        ) {
            val item = getItem(position)
            val context = holder.itemView.context
            val preview =
                item.lastMessage.content
                    .replace(Regex("\\s+"), " ")
                    .trim()
            holder.binding.title.text = item.title
            holder.binding.preview.text =
                when (item.lastMessage.role) {
                    AiChatRole.USER -> context.getString(R.string.ai_chat_history_you, preview)
                    AiChatRole.ASSISTANT -> context.getString(R.string.ai_chat_history_ai, preview)
                }
            val date = DateUtils.formatDateTime(context, item.lastAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
            holder.binding.meta.text =
                context.resources.getQuantityString(R.plurals.ai_chat_history_meta, item.messageCount, item.messageCount, date)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        companion object {
            val DIFF =
                object : DiffUtil.ItemCallback<AiChatHistoryItem>() {
                    override fun areItemsTheSame(
                        oldItem: AiChatHistoryItem,
                        newItem: AiChatHistoryItem,
                    ) = oldItem.nid == newItem.nid

                    override fun areContentsTheSame(
                        oldItem: AiChatHistoryItem,
                        newItem: AiChatHistoryItem,
                    ) = oldItem == newItem
                }
        }
    }
}

private const val TITLE_MAX_LENGTH = 80

/** Plain-text front of each note's first card, keyed by note id; notes that no longer exist are omitted. */
internal fun Collection.noteTitles(nids: List<NoteId>): Map<NoteId, String> =
    nids
        .mapNotNull { nid ->
            val card =
                try {
                    getNote(nid).cards(this).firstOrNull()
                } catch (e: BackendNotFoundException) {
                    null
                } ?: return@mapNotNull null
            val title =
                stripHTMLAndSpecialFields(card.question(this))
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(TITLE_MAX_LENGTH)
            nid to title
        }.toMap()
