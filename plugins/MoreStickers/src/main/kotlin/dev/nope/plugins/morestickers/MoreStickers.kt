package dev.nope.plugins.morestickers

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageButton
import com.aliucord.*
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.entities.Plugin.SettingsTab
import com.aliucord.patcher.after
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.dimen.DimenUtils
import com.lytefast.flexinput.fragment.FlexInputFragment

import com.lytefast.flexinput.R
val logger = Logger("MoreStickers")

@AliucordPlugin(requiresRestart = false)
class MoreStickers : Plugin() {
    override fun start(context: Context) {
        logger.info("Plugin start() called")
        
        // Initialize settings page (uses PluginManager to look up plugin instance)
        settingsTab = SettingsTab(MoreStickersSettings::class.java)

        // Patch FlexInputFragment to add our sticker button
        patcher.after<FlexInputFragment>(
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
        ) { param ->
                try {
                logger.debug("FlexInputFragment.onViewCreated: patching sticker button")
                val root = param.args[0] as View
                val mainContainerId = Utils.getResId("main_input_container", "id")
                val expressionContainerId = Utils.getResId("expression_btn_container", "id")

                logger.info("IDs: mainContainer=$mainContainerId, expressionContainer=$expressionContainerId")

                val mainContainer = root.findViewById<LinearLayout>(mainContainerId)
                if (mainContainer == null) {
                    logger.warn("Could not find mainContainer by ID")
                    return@after
                }

                val expressionContainer = root.findViewById<FrameLayout>(expressionContainerId)
                if (expressionContainer == null) {
                    logger.warn("Could not find expressionContainer by ID")
                    return@after
                }

                // Check if button already exists (avoid duplicates)
                if (mainContainer.findViewWithTag<View>(BUTTON_TAG) != null) {
                    logger.info("Sticker button already added, skipping")
                    return@after
                }

                logger.info("Creating and adding sticker button")
                val pad = DimenUtils.dpToPixels(8)
                val button = AppCompatImageButton(root.context).apply {
                    tag = BUTTON_TAG
                    contentDescription = "More stickers"
                    background = null
                    setPadding(pad, pad, pad, pad)
                    setImageResource(Utils.getResId("ic_sticker_icon_24dp", "drawable"))
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        ColorCompat.getThemedColor(context, R.b.colorInteractiveNormal),
                    )
                    setOnClickListener {
                        logger.info("Sticker button clicked")
                        val activity = Utils.appActivity
                        activity?.let {
                            logger.info("Showing MoreStickersSheet")
                            MoreStickersSheet(settings).show(it.supportFragmentManager, "MoreStickersSheet")
                        } ?: run {
                            logger.warn("AppActivity is null, cannot show sticker sheet")
                        }
                    }
                }

                val index = mainContainer.indexOfChild(expressionContainer)
                if (index >= 0) {
                    logger.info("Adding button at index $index (before expressionContainer)")
                    mainContainer.addView(button, index)
                } else {
                    logger.info("Adding button at end of mainContainer")
                    mainContainer.addView(button)
                }

                logger.info("Sticker button added successfully")
            } catch (e: Exception) {
                logger.error("Error patching FlexInputFragment", e)
            }
        }
    }

    override fun stop(context: Context) {
        logger.info("Plugin stop() called, unpatching all hooks")
        patcher.unpatchAll()
    }

    private companion object {
        const val BUTTON_TAG = "more-stickers-button"
    }
}
