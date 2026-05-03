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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
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

                // Patch WidgetExpressionTray to add a fourth segment and content view at runtime
                try {
                    patcher.after<com.discord.widgets.chat.input.expression.WidgetExpressionTray>(
                        "onViewBound",
                        View::class.java,
                    ) { param: XC_MethodHook.MethodHookParam ->
                        try {
                            logger.debug("WidgetExpressionTray.onViewBound: injecting MoreStickers tab")
                            val root = param.args[0] as View
                            val segId = Utils.getResId("expression_tray_segmented_control", "id")
                            val contentContainerId = Utils.getResId("expression_tray_content_container", "id")
                            val segControl = root.findViewById<com.discord.views.segmentedcontrol.SegmentedControlContainer?>(segId)
                            val contentContainer = root.findViewById<ViewGroup?>(contentContainerId)

                            if (segControl == null || contentContainer == null) {
                                logger.warn("Could not find segmented control or content container")
                                return@after
                            }

                            // Avoid duplicate injection
                            if (root.findViewWithTag<View>("more-stickers-segment") != null) {
                                logger.info("MoreStickers segment already injected, skipping")
                                return@after
                            }

                            val inflater = root.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                            // Create a CardSegment instance (must be CardSegment, not raw layout)
                            val cardSegment = com.discord.views.segmentedcontrol.CardSegment(root.context, null)
                            // ensure equal widths: match the style (width=0, weight=1)
                            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                            cardSegment.layoutParams = lp
                            cardSegment.tag = "more-stickers-segment"
                            // set text label
                            cardSegment.setText("More")

                            // Add the CardSegment to the segmented control
                            segControl.addView(cardSegment)

                            // Create a content frame and add it to the content container
                            val frame = FrameLayout(root.context).apply {
                                tag = "more-stickers-content"
                                id = View.generateViewId()
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                visibility = View.GONE
                            }
                            // simple placeholder content for the prototype
                            val placeholder = TextView(root.context).apply {
                                text = "MoreStickers (plugin)"
                                setPadding(20, 20, 20, 20)
                            }
                            frame.addView(placeholder)
                            contentContainer.addView(frame)

                            // Wire selection listeners: re-run SegmentedControlContainer.a(selectedIndex)
                            // so the container sets its original OnClickListeners for all children (including ours).
                            try {
                                val aMethod = segControl.javaClass.getDeclaredMethod("a", Int::class.javaPrimitiveType)
                                aMethod.isAccessible = true
                                // default to emoji tab (index 0)
                                aMethod.invoke(segControl, 0)
                            } catch (e: Exception) {
                                logger.debug("Could not invoke SegmentedControlContainer.a(...): ${e.message}")
                            }

                            // Now wrap each child's original OnClickListener (via reflection) so we can toggle our content.
                            try {
                                val getListenerInfo = View::class.java.getDeclaredMethod("getListenerInfo")
                                getListenerInfo.isAccessible = true
                                val listenerInfoClass = Class.forName("android.view.View\$ListenerInfo")
                                val mOnClickListenerField = listenerInfoClass.getDeclaredField("mOnClickListener")
                                mOnClickListenerField.isAccessible = true

                                val childCount = segControl.childCount
                                val emojiId = Utils.getResId("expression_tray_emoji_picker_content", "id")
                                val gifId = Utils.getResId("expression_tray_gif_picker_content", "id")
                                val stickerId = Utils.getResId("expression_tray_sticker_picker_content", "id")

                                for (i in 0 until childCount) {
                                    val child = segControl.getChildAt(i)
                                    val li = getListenerInfo.invoke(child)
                                    @Suppress("UNCHECKED_CAST")
                                    val original = mOnClickListenerField.get(li) as? View.OnClickListener

                                    // set wrapper listener
                                    child.setOnClickListener { v ->
                                        try {
                                            original?.onClick(v)
                                        } catch (e: Exception) {
                                            logger.error("Error invoking original child click", e)
                                        }
                                        try {
                                            if (i == segControl.indexOfChild(cardSegment)) {
                                                // our tab selected
                                                root.findViewById<View?>(emojiId)?.visibility = View.GONE
                                                root.findViewById<View?>(gifId)?.visibility = View.GONE
                                                root.findViewById<View?>(stickerId)?.visibility = View.GONE
                                                frame.visibility = View.VISIBLE
                                                segControl.setSelectedIndex(i)
                                            } else {
                                                // other tab selected
                                                root.findViewById<View?>(emojiId)?.visibility = View.VISIBLE
                                                root.findViewById<View?>(gifId)?.visibility = View.VISIBLE
                                                root.findViewById<View?>(stickerId)?.visibility = View.VISIBLE
                                                frame.visibility = View.GONE
                                                segControl.setSelectedIndex(i)
                                            }
                                        } catch (e: Exception) {
                                            logger.error("Error toggling content after child click", e)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Could not wrap child click listeners", e)
                            }

                            logger.info("Injected MoreStickers segment and content")
                        } catch (e: Exception) {
                            logger.error("Error injecting MoreStickers tab", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to patch WidgetExpressionTray", e)
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
