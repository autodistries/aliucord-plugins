package dev.nope.plugins.morestickers

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.PluginManager
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.dimen.DimenUtils

import com.lytefast.flexinput.R

/**
 * Settings page for MoreStickers plugin.
 * Manages import/export of sticker packs and displays installed packs.
 */
class MoreStickersSettings : SettingsPage() {
    private val plugin by lazy {
        logger.debug("Looking up MoreStickers plugin via PluginManager")
        PluginManager.plugins["MoreStickers"] ?: throw RuntimeException("MoreStickers plugin not found")
    }
    private lateinit var packList: LinearLayout

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("MoreStickers")
        
        logger.debug("MoreStickersSettings.onViewBound() called")

        val context = requireContext()
        val pad8 = DimenUtils.dpToPixels(8)
        val dir = StickerStore.getPacksDir()
        val file = StickerStore.getPacksFile()
        
        logger.debug("Pack folder location: ${dir.absolutePath}")
        logger.debug("Pack file location: ${file.absolutePath}")

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val fileHint = TextView(context).apply {
            text = "Pack folder: ${dir.absolutePath}\nDrop .json or .stickerpack files here"
        }
        container.addView(fileHint)
        
        logger.debug("Creating import button")
        container.addView(createActionItem("Import packs from folder") {
            logger.debug("Import button clicked")
            Utils.threadPool.execute {
                try {
                    logger.debug("Starting import from ${dir.absolutePath}")
                    val count = StickerStore.importFromFile(plugin.settings, dir)
                    logger.info("Successfully imported $count pack(s)")
                    Utils.showToast("Imported $count pack(s)")
                } catch (e: Exception) {
                    logger.error("Import failed", e)
                    Utils.showToast("Import failed: ${e.message}")
                }
                Utils.mainThread.post {
                    logger.debug("Refreshing pack list after import")
                    refreshPackList()
                }
            }
        })

        logger.debug("Creating export button")
        container.addView(createActionItem("Export packs to file") {
            logger.debug("Export button clicked")
            Utils.threadPool.execute {
                try {
                    logger.debug("Starting export to ${file.absolutePath}")
                    val count = StickerStore.exportToFile(plugin.settings, file)
                    logger.info("Successfully exported $count pack(s)")
                    Utils.showToast("Exported $count pack(s)")
                } catch (e: Exception) {
                    logger.error("Export failed", e)
                    Utils.showToast("Export failed: ${e.message}")
                }
            }
        })

        logger.debug("Creating open folder button")
        container.addView(createActionItem("Open pack folder") {
            val parentDir = file.parentFile
            if (parentDir != null) {
                logger.debug("Open folder button clicked, opening ${parentDir.absolutePath}")
                Utils.launchFileExplorer(parentDir)
            } else {
                logger.warn("Parent directory is null, cannot open folder")
            }
        })

        val header = TextView(context).apply {
            text = "Installed Packs"
        }
        header.setPadding(0, pad8, 0, pad8)
        container.addView(header)

        packList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(packList)

        addView(container)
        refreshPackList()
    }

    private fun createActionItem(title: String, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            text = title
            setOnClickListener {
                logger.debug("Action item clicked: $title")
                onClick()
            }
        }
    }

    private fun refreshPackList() {
        try {
            logger.debug("refreshPackList: loading packs from settings")
            val context = requireContext()
            val packs = StickerStore.getPacks(plugin.settings)
            logger.debug("Loaded ${packs.size} pack(s) from settings")
            val pad8 = DimenUtils.dpToPixels(8)

            packList.removeAllViews()

            if (packs.isEmpty()) {
                logger.debug("No packs found, showing empty state")
                packList.addView(
                    TextView(context).apply {
                        text = "No packs imported yet"
                    },
                )
                return
            }

            packs.forEachIndexed { index, pack ->
                logger.debug("Rendering pack [$index]: ${pack.title} (id=${pack.id})")
                
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, pad8, 0, pad8)
                }

                val title = TextView(context).apply {
                    text = "${pack.title} (${pack.stickers.size} stickers)"
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }

                val remove = TextView(context).apply {
                    text = "Remove"
                    setTextColor(ColorCompat.getThemedColor(context, R.b.colorStatusDanger))
                    setOnClickListener {
                        logger.debug("Remove clicked for pack: ${pack.title} (id=${pack.id})")
                        StickerStore.removePack(plugin.settings, pack.id)
                        logger.info("Removed pack: ${pack.title}")
                        Utils.showToast("Removed ${pack.title}")
                        refreshPackList()
                    }
                }

                row.addView(title)
                row.addView(remove)
                packList.addView(row)
            }
            logger.debug("Pack list refreshed, displaying ${packs.size} pack(s)")
        } catch (e: Exception) {
            logger.error("Error refreshing pack list", e)
        }
    }
}
