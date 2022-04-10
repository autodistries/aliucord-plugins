package dev.nope.plugins.copyurlinsteadofshare

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
import com.discord.databinding.WidgetChatListActionsBinding
import com.discord.stores.StoreStream
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.lytefast.flexinput.R
import java.lang.reflect.InvocationTargetException


@AliucordPlugin(requiresRestart = false /* Whether your plugin requires a restart after being installed/updated */)
class CopyUrlInsteadOfShareMessages : Plugin() {
    override fun start(context: Context) {
        val shareMessagesId = Utils.getResId("dialog_chat_actions_share", "id")

        val icon = ContextCompat.getDrawable(context, R.e.ic_link_white_24dp)!!
            .mutate()

        val copyMessageUrlId = View.generateViewId()

        with(WidgetChatListActions::class.java) {
            val getBinding = getDeclaredMethod("getBinding").apply { isAccessible = true }



            patcher.patch(
                getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                Hook { callFrame ->
                    val shareMessagesId = Utils.getResId("dialog_chat_actions_share", "id")
                    val binding =
                        getBinding.invoke(callFrame.thisObject) as WidgetChatListActionsBinding
                    val ShareMessages = binding.a.findViewById<TextView>(shareMessagesId).apply {
                        visibility = View.VISIBLE
                    }

                    val linearLayout =
                        (callFrame.args[0] as NestedScrollView).getChildAt(0) as LinearLayout
                    val ctx = linearLayout.context

                    icon.setTint(ColorCompat.getThemedColor(ctx, R.b.colorInteractiveNormal))

                    val copyMessageUrl =
                        TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                            text = ctx.getString(R.h.copy_link)
                            id = copyMessageUrlId
                            typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_medium)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)


                        }

                    linearLayout.removeView(ShareMessages)
                    linearLayout.addView(copyMessageUrl, 13)
                })

            patcher.patch(
                getDeclaredMethod("configureUI", WidgetChatListActions.Model::class.java),
                Hook { callFrame ->
                    try {
                        val binding =
                            getBinding.invoke(callFrame.thisObject) as WidgetChatListActionsBinding
                        val ShareMessages =
                            binding.a.findViewById<TextView>(copyMessageUrlId).apply {
                                visibility = View.VISIBLE
                            }

                        ShareMessages.setOnClickListener {
                            try {
                                val msg = (callFrame.args[0] as WidgetChatListActions.Model).message
                                var guildId =
                                    StoreStream.getChannels().getChannel(msg.channelId).h()
                                        .toString()
                                if (guildId == "0") guildId = "@me"
                                val imageUri =
                                    "https://discord.com/channels/${guildId}/${msg.channelId}/${msg.id}"

                                Utils.setClipboard(
                                    "message link",
                                    imageUri.toString() as CharSequence
                                )
                                showToast("Copied $imageUri", showLonger = false)
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
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}
