package dev.nope.plugins.copyurlinsteadofshare

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
import com.discord.app.AppBottomSheet
import com.discord.databinding.WidgetChatListActionsBinding
import com.discord.utilities.color.ColorCompat
import com.discord.widgets.chat.list.actions.WidgetChatListActions
import com.lytefast.flexinput.R
import java.lang.reflect.InvocationTargetException


@AliucordPlugin(requiresRestart = false)
class MessageLinkContext : Plugin() {

    /* No settings tab for you because Cannot access 'com.discord.app.AppLogger$a' which is a supertype of 'dev.nope.plugins.copyurlinsteadofshare.HelpMeeee'. Check your module classpath for missing or conflicting dependencies
    init {
        settingsTab = SettingsTab(
            Halp::class.java,
            SettingsTab.Type.BOTTOM_SHEET
        ).withArgs(settings)
    }
    */

    @SuppressLint("SetTextI18n")
    override fun start(context: Context) {

        val icon = ContextCompat.getDrawable(context, R.e.ic_diag_link_24dp)!!
            .mutate()

        val copyMessageUrlViewId = View.generateViewId()

        with(WidgetChatListActions::class.java) {
            val getBinding = getDeclaredMethod("getBinding").apply { isAccessible = true }

            val replaceShare = settings.getBool("replaceShare", true)

            patcher.patch( //creating the option
                getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                Hook { callFrame ->
                    val shareMessagesViewId = Utils.getResId("dialog_chat_actions_share", "id")
                    val binding =
                        getBinding.invoke(callFrame.thisObject) as WidgetChatListActionsBinding
                    val shareMessageView =
                        binding.a.findViewById<TextView>(shareMessagesViewId).apply {
                            visibility = View.VISIBLE
                        }
                    val linearLayout =
                        (callFrame.args[0] as NestedScrollView).getChildAt(0) as LinearLayout
                    val ctx = linearLayout.context
                    icon.setTint(ColorCompat.getThemedColor(ctx, R.b.colorInteractiveNormal))
                    val copyMessageUrl =
                        TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                            text = "Copy Message Link"
                            id = copyMessageUrlViewId
                            typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_medium)
                            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
                        }
                    var replacementId = linearLayout.indexOfChild(shareMessageView)
                    linearLayout.removeView(shareMessageView) // poof

                    linearLayout.addView(
                        copyMessageUrl,
                        replacementId
                    )
                })

            patcher.patch( //setting onClickListener
                getDeclaredMethod("configureUI", WidgetChatListActions.Model::class.java),
                Hook { callFrame ->
                    try {
                        val binding =
                            getBinding.invoke(callFrame.thisObject) as WidgetChatListActionsBinding
                        val shareMessageView =
                            binding.a.findViewById<TextView>(copyMessageUrlViewId).apply {
                                visibility = View.VISIBLE
                            }

                        shareMessageView.setOnClickListener {
                            try {
                                val msg = (callFrame.args[0] as WidgetChatListActions.Model).message
                                val guild =
                                    (callFrame.args[0] as WidgetChatListActions.Model).guild // because msg.guildId is null
                                val messageUri = String.format(
                                    "https://discord.com/channels/%s/%s/%s",
                                    try {
                                        guild.id
                                    } catch (e: Throwable) { // for DMs
                                        "@me"
                                    },
                                    msg.channelId,
                                    msg.id
                                )
                                Utils.setClipboard(
                                    "message link",
                                    messageUri
                                )
                                showToast("Copied url $messageUri", showLonger = false)

                                val bottomSheetDismisser =
                                    AppBottomSheet::class.java.getDeclaredMethod("dismiss") // because cannot access shit again
                                bottomSheetDismisser.invoke((callFrame.thisObject as WidgetChatListActions))
                            } catch (e: IllegalAccessException) {
                                e.printStackTrace()
                            } catch (e: InvocationTargetException) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) { //yes generic maybe works idk
                        e.printStackTrace()
                    }
                })
        }
    }

    override fun stop(context: Context) = patcher.unpatchAll()
}


