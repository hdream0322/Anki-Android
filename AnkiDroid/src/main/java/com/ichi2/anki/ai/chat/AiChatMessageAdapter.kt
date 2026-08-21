package com.ichi2.anki.ai.chat

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.ai.AiChatMessage
import com.ichi2.anki.ai.AiChatRole
import com.ichi2.anki.databinding.ItemAiChatMessageBinding

class AiChatMessageAdapter : RecyclerView.Adapter<AiChatMessageAdapter.Holder>() {
    private var items: List<AiChatMessage> = emptyList()

    fun submitList(messages: List<AiChatMessage>) {
        items = messages
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val binding = ItemAiChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) {
        val message = items[position]
        holder.binding.messageText.text = message.content
        val layoutParams = holder.binding.messageText.layoutParams as android.widget.FrameLayout.LayoutParams
        layoutParams.gravity = if (message.role == AiChatRole.USER) Gravity.END else Gravity.START
        holder.binding.messageText.layoutParams = layoutParams
    }

    override fun getItemCount() = items.size

    class Holder(
        val binding: ItemAiChatMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root)
}
