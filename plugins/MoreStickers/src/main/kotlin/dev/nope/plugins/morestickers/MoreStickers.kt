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

                            // Create a FragmentContainerView and add it to the content container
                            val fragView = androidx.fragment.app.FragmentContainerView(root.context).apply {
                                tag = "more-stickers-content"
                                id = View.generateViewId()
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                visibility = View.GONE
                            }
                            contentContainer.addView(fragView)

                            // Install our fragment into the tray's child fragment manager so it behaves like other tabs
                            try {
                                val trayFragment = param.thisObject as? com.discord.app.AppFragment
                                trayFragment?.childFragmentManager?.beginTransaction()?.replace(fragView.id, MoreStickersFragment(), "more_stickers_fragment")?.commit()
                            } catch (e: Exception) {
                                logger.error("Could not attach MoreStickersFragment", e)
                            }

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
                                // original tab count is childCount - 1 (we added one)
                                val originalTabCount = if (childCount > 0) childCount - 1 else childCount
                                val emojiId = Utils.getResId("expression_tray_emoji_picker_content", "id")
                                val gifId = Utils.getResId("expression_tray_gif_picker_content", "id")
                                val stickerId = Utils.getResId("expression_tray_sticker_picker_content", "id")

                                for (i in 0 until childCount) {
                                    val child = segControl.getChildAt(i)
                                    val li = getListenerInfo.invoke(child)
                                    @Suppress("UNCHECKED_CAST")
                                    val original = mOnClickListenerField.get(li) as? View.OnClickListener

                                    // set wrapper listener; avoid invoking original behaviour for our custom tab
                                    child.setOnClickListener { v ->
                                        try {
                                            if (i < originalTabCount) {
                                                original?.onClick(v)
                                            }
                                        } catch (e: Exception) {
                                            logger.error("Error invoking original child click", e)
                                        }
                                        try {
                                            if (i == segControl.indexOfChild(cardSegment)) {
                                                // our tab selected — hide built-in pickers, show our fragment container
                                                root.findViewById<View?>(emojiId)?.visibility = View.GONE
                                                root.findViewById<View?>(gifId)?.visibility = View.GONE
                                                root.findViewById<View?>(stickerId)?.visibility = View.GONE
                                                fragView.visibility = View.VISIBLE
                                                segControl.setSelectedIndex(i)
                                            } else {
                                                // other tab selected — show built-in pickers, hide our container
                                                root.findViewById<View?>(emojiId)?.visibility = View.VISIBLE
                                                root.findViewById<View?>(gifId)?.visibility = View.VISIBLE
                                                root.findViewById<View?>(stickerId)?.visibility = View.VISIBLE
                                                fragView.visibility = View.GONE
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
