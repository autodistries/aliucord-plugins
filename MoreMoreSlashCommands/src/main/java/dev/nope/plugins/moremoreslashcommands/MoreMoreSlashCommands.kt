package dev.nope.plugins.blurnsfw

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import com.aliucord.Constants
import com.aliucord.Utils
import com.aliucord.Utils.showToast
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.wrappers.ChannelWrapper
import com.discord.databinding.WidgetChatListActionsBinding
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.lytefast.flexinput.R
import java.lang.reflect.InvocationTargetException


@AliucordPlugin
class ShareMessages : Plugin() {
    @SuppressLint("SetTextI18n")
    override fun start(context: Context) {
        val ShareMessagesId = View.generateViewId()
        val icon = ContextCompat.getDrawable(context, R.e.ic_link_white_24dp)!!
            .mutate()

        with(WidgetChatListActions::class.java) {
            val getBinding = getDeclaredMethod("getBinding").apply { isAccessible = true }
            /*  val addReaction =
                  getDeclaredMethod("addReaction", Emoji::class.java).apply { isAccessible = true }
  */
            patcher.patch(
                getDeclaredMethod("configureUI", WidgetChatListActions.Model::class.java),
                Hook { callFrame ->
                    try {
                        val binding =
                            getBinding.invoke(callFrame.thisObject) as WidgetChatListActionsBinding
                        val ShareMessages = binding.a.findViewById<TextView>(ShareMessagesId).apply {
                            visibility = View.VISIBLE
                        }

                        if (!ShareMessages.hasOnClickListeners()) ShareMessages.setOnClickListener {
                            try {
                                val msg = (callFrame.args[0] as WidgetChatListActions.Model).message
                                var guildId = StoreStream.getChannels().getChannel(msg.channelId).f().toString()
                                if (guildId == "0") guildId = "@me"
                                val imageUri = "https://discord.com/channels/${guildId}/${msg.channelId}/${msg.id}"

                                Utils.setClipboard("upper data",imageUri.toString() as CharSequence)

                                showToast("Copied "+imageUri, showLonger = false)
                                (callFrame.thisObject as WidgetChatListActions).dismiss()
                            } catch (e: IllegalAccessException) {
                                e.printStackTrace()
                            } catch (e: InvocationTargetException) {
                                e.printStackTrace()
                            }
                        }
                    } catch (ignored: Throwable) {
                    }
                })

            patcher.patch(
                getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                Hook { callFrame ->
                    val linearLayout =
                        (callFrame.args[0] as NestedScrollView).getChildAt(0) as LinearLayout
                    val ctx = linearLayout.context

                    icon?.setTint(ColorCompat.getThemedColor(ctx, R.b.colorInteractiveNormal))

                    val ShareMessages = TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "Copy url"
                        id = ShareMessagesId
                        typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_medium)
                        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                    }

                    linearLayout.addView(ShareMessages, 1)
                })
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}