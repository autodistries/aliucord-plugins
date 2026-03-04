package dev.nope.plugins.searchhighlight

import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.stores.StoreSearchQuery
import com.discord.stores.StoreStream
import com.discord.utilities.view.text.SimpleDraweeSpanTextView
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapter
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.search.results.WidgetSearchResults

@AliucordPlugin(requiresRestart = false)
@Suppress("unused")
class SearchHighlight : Plugin() {

    override fun start(context: Context) {
        val itemTextField = WidgetChatListAdapterItemMessage::class.java
            .getDeclaredField("itemText")
            .apply { isAccessible = true }

        val currentSearchStateField = StoreSearchQuery::class.java
            .getDeclaredField("currentSearchState")
            .apply { isAccessible = true }

        patcher.after<WidgetChatListAdapterItemMessage>(
            "onConfigure",
            Int::class.java,
            ChatListEntry::class.java,
        ) {
            // Check if we're in a search results context
            val chatAdapter = adapter ?: return@after
            if (chatAdapter.data !is WidgetSearchResults.Model) return@after

            // Get the message text view
            val textView = itemTextField.get(this) as? SimpleDraweeSpanTextView ?: return@after
            val text = textView.text as? Spannable ?: return@after
            if (text.isEmpty()) return@after

            // Get current search terms from the store
            val storeSearchQuery = StoreStream.getSearch().storeSearchQuery
            val searchState = currentSearchStateField.get(storeSearchQuery)
                ?: return@after
            val query = com.discord.utilities.search.network.state.SearchState::class.java
                .getMethod("getSearchQuery")
                .invoke(searchState)
                ?: return@after
            val params = com.discord.utilities.search.network.SearchQuery::class.java
                .getMethod("getParams")
                .invoke(query) as? Map<*, *>
                ?: return@after

            @Suppress("UNCHECKED_CAST")
            val contentTerms = params["content"] as? java.util.List<*> ?: return@after

            // Use only Java APIs to avoid Kotlin stdlib compat issues
            val terms = java.util.ArrayList<String>()
            val iter = contentTerms.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()?.toString() ?: continue
                val parts = (entry as java.lang.String).split(" ")
                var i = 0
                while (i < parts.size) {
                    val part = parts[i].trim()
                    if (part.length > 0) terms.add(part)
                    i++
                }
            }
            if (terms.size == 0) return@after

            highlightTerms(text, terms)
        }
    }

    private fun highlightTerms(text: Spannable, terms: java.util.ArrayList<String>) {
        // Semi-transparent yellow/gold highlight that works on dark backgrounds
        val highlightColor = Color.argb(100, 255, 200, 0)
        val content = text.toString().lowercase(java.util.Locale.ROOT)

        var i = 0
        while (i < terms.size) {
            val lowerTerm = terms[i].lowercase(java.util.Locale.ROOT)
            var start = 0
            while (true) {
                val index = content.indexOf(lowerTerm, start)
                if (index < 0) break
                text.setSpan(
                    BackgroundColorSpan(highlightColor),
                    index,
                    index + lowerTerm.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                start = index + lowerTerm.length
            }
            i++
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
