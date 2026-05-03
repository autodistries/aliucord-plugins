package dev.nope.plugins.morestickers

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.*
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.widgets.BottomSheet
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.dimen.DimenUtils
import com.discord.utilities.images.MGImages
import com.discord.utilities.premium.PremiumUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.lytefast.flexinput.R
import com.lytefast.flexinput.model.Attachment

private const val RECENT_PACK_ID = "recent"

class MoreStickersSheet(private val settings: SettingsAPI) : BottomSheet() {
    private lateinit var packAdapter: PackAdapter
    private lateinit var stickerAdapter: StickerAdapter
    private lateinit var emptyView: TextView

    private var packs: List<StickerPack> = emptyList()
    private var recent: List<Sticker> = emptyList()
    private var selectedPackId = RECENT_PACK_ID
    private var query: String = ""

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)
        val context = requireContext()
        packs = StickerStore.getPacks(settings)
        recent = StickerStore.getRecent(settings)

        val pad16 = DimenUtils.dpToPixels(16)
        val pad8 = DimenUtils.dpToPixels(8)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad16, pad16, pad16, pad16)
        }

        val title = TextView(context).apply {
            text = "MoreStickers"
            textSize = 16f
            setTextColor(ColorCompat.getThemedColor(context, R.b.colorTextBrand))
        }
        root.addView(title)

        val search = EditText(context).apply {
            hint = "Search stickers"
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = pad8
            }
            addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        query = s?.toString().orEmpty()
                        updateStickers()
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    }
                },
            )
        }
        root.addView(search)

        packAdapter = PackAdapter(context, emptyList(), selectedPackId) { item ->
            selectedPackId = item.id
            packAdapter.setSelected(item.id)
            updateStickers()
        }
        val packRecycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = packAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DimenUtils.dpToPixels(48),
            ).apply {
                bottomMargin = pad8
            }
        }
        root.addView(packRecycler)

        emptyView = TextView(context).apply {
            text = "No stickers to show. Import packs from settings."
            visibility = View.GONE
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(ColorCompat.getThemedColor(context, R.b.colorTextMuted))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = pad8
            }
        }
        root.addView(emptyView)

        stickerAdapter = StickerAdapter(context) { sticker ->
            sendSticker(sticker)
        }
        val stickerRecycler = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = stickerAdapter
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }
        root.addView(stickerRecycler)

        addView(root)
        updatePackItems()
        updateStickers()
    }

    private fun updatePackItems() {
        val items = buildPackItems()
        packAdapter.setItems(items, selectedPackId)
    }

    private fun buildPackItems(): List<PackItem> {
        val items = mutableListOf(
            PackItem(
                id = RECENT_PACK_ID,
                title = "Recent",
                iconUrl = null,
                isRecent = true,
            ),
        )
        packs.forEach { pack ->
            val iconUrl = pack.logo?.image ?: pack.stickers.firstOrNull()?.image
            items.add(
                PackItem(
                    id = pack.id,
                    title = pack.title,
                    iconUrl = iconUrl,
                    isRecent = false,
                ),
            )
        }
        return items
    }

    private fun updateStickers() {
        val stickers = when (selectedPackId) {
            RECENT_PACK_ID -> recent
            else -> packs.firstOrNull { it.id == selectedPackId }?.stickers ?: emptyList()
        }

        val filtered = if (query.trim().isEmpty()) {
            stickers
        } else {
            stickers.filter { it.title.contains(query, ignoreCase = true) }
        }

        emptyView.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        stickerAdapter.setItems(filtered)
    }

    private fun sendSticker(sticker: Sticker) {
        val context = context ?: return
        val channelId = StoreStream.getChannelsSelected().id
        if (channelId <= 0L) {
            Utils.showToast("No channel selected")
            return
        }

        Utils.threadPool.execute {
            try {
                val file = StickerStore.downloadStickerToFile(sticker, context.cacheDir)
                val uri = Uri.fromFile(file)
                val attachment = Attachment.toAttachment(uri, context.contentResolver)
                val currentSizeMb = file.length().toFloat() / (1024f * 1024f)
                val maxSizeMb = PremiumUtils.INSTANCE.getMaxFileSizeMB(StoreStream.getUsers().me)
                val request = com.discord.widgets.chat.MessageManager.AttachmentsRequest(
                    currentSizeMb,
                    maxSizeMb,
                    listOf(attachment),
                )

                val messageManager = com.discord.widgets.chat.MessageManager(
                    context,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    510,
                    null,
                )
                val sent = messageManager.sendMessage(
                    "",
                    emptyList(),
                    request,
                    channelId,
                    emptyList(),
                    false,
                    { _: Int, _: Int -> },
                    { _: Int, _: Boolean -> },
                    { _: Any? -> },
                )

                if (!sent) {
                    Utils.mainThread.post {
                        Utils.showToast("Failed to send sticker")
                    }
                }

                StickerStore.addRecent(settings, sticker)
                Utils.mainThread.post {
                    recent = StickerStore.getRecent(settings)
                    if (selectedPackId == RECENT_PACK_ID) {
                        updateStickers()
                    }
                    dismiss()
                }
            } catch (e: Exception) {
                Utils.showToast("Failed to send sticker: ${'$'}{e.message}")
            }
        }
    }

    private data class PackItem(
        val id: String,
        val title: String,
        val iconUrl: String?,
        val isRecent: Boolean,
    )

    private class PackAdapter(
        private val context: Context,
        private var items: List<PackItem>,
        private var selectedId: String,
        private val onSelect: (PackItem) -> Unit,
    ) : RecyclerView.Adapter<PackViewHolder>() {
        private val size = DimenUtils.dpToPixels(32)
        private val padding = DimenUtils.dpToPixels(6)
        private val selectedBg = ColorCompat.getThemedColor(context, R.b.colorBackgroundSecondaryAlt)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val container = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    size + padding * 2,
                    size + padding * 2,
                )
            }
            val image = SimpleDraweeView(context).apply {
                layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            }
            container.addView(image)
            return PackViewHolder(container, image)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            val item = items[position]
            holder.container.setBackgroundColor(if (item.id == selectedId) selectedBg else 0)
            if (item.iconUrl == null || item.iconUrl.trim().isEmpty()) {
                val resId = if (item.isRecent) {
                    Utils.getResId("ic_recent_24dp", "drawable")
                } else {
                    Utils.getResId("ic_sticker_icon_24dp", "drawable")
                }
                holder.image.setImageResource(resId)
            } else {
                MGImages.setImage(holder.image, item.iconUrl, 0, 0, false, null, null)
            }
            holder.container.setOnClickListener { onSelect(item) }
            holder.container.contentDescription = item.title
        }

        override fun getItemCount(): Int = items.size

        fun setItems(newItems: List<PackItem>, selectedId: String) {
            items = newItems
            this.selectedId = selectedId
            notifyDataSetChanged()
        }

        fun setSelected(id: String) {
            selectedId = id
            notifyDataSetChanged()
        }
    }

    private class PackViewHolder(
        val container: FrameLayout,
        val image: SimpleDraweeView,
    ) : RecyclerView.ViewHolder(container)

    private class StickerAdapter(
        private val context: Context,
        private val onClick: (Sticker) -> Unit,
    ) : RecyclerView.Adapter<StickerViewHolder>() {
        private val size = DimenUtils.dpToPixels(96)
        private val padding = DimenUtils.dpToPixels(4)
        private var items: List<Sticker> = emptyList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
            val container = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(size, size).apply {
                    bottomMargin = padding
                }
                setPadding(padding, padding, padding, padding)
            }
            val image = SimpleDraweeView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            container.addView(image)
            return StickerViewHolder(container, image)
        }

        override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
            val sticker = items[position]
            MGImages.setImage(holder.image, sticker.image, 0, 0, false, null, null)
            holder.itemView.setOnClickListener { onClick(sticker) }
            holder.itemView.contentDescription = sticker.title
        }

        override fun getItemCount(): Int = items.size

        fun setItems(newItems: List<Sticker>) {
            items = newItems
            notifyDataSetChanged()
        }
    }

    private class StickerViewHolder(
        container: FrameLayout,
        val image: SimpleDraweeView,
    ) : RecyclerView.ViewHolder(container)
}
