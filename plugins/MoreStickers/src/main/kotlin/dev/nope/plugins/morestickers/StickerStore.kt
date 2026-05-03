package dev.nope.plugins.morestickers

import com.aliucord.Constants
import com.aliucord.Http
import com.aliucord.api.SettingsAPI
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import kotlin.jvm.JvmName

object StickerStore {
    private const val KEY_PACKS = "packs"
    private const val KEY_RECENT = "recent"
    private const val RECENT_LIMIT = 16

    fun getPacks(settings: SettingsAPI): MutableList<StickerPack> {
        return settings.getString(KEY_PACKS, "[]").toStickerPackList().toMutableList()
    }

    fun setPacks(settings: SettingsAPI, packs: List<StickerPack>) {
        settings.setString(KEY_PACKS, packs.toJsonArray().toString())
    }

    fun addPack(settings: SettingsAPI, pack: StickerPack): List<StickerPack> {
        val packs = getPacks(settings)
        val normalized = normalizePack(pack)
        val existingIndex = packs.indexOfFirst { it.id == normalized.id }
        if (existingIndex >= 0) {
            packs[existingIndex] = normalized
        } else {
            packs.add(normalized)
        }
        setPacks(settings, packs)
        return packs
    }

    fun removePack(settings: SettingsAPI, packId: String): List<StickerPack> {
        val packs = getPacks(settings).filter { it.id != packId }
        setPacks(settings, packs)
        removeRecentByPackId(settings, packId)
        return packs
    }

    fun getRecent(settings: SettingsAPI): MutableList<Sticker> {
        return settings.getString(KEY_RECENT, "[]").toStickerList().toMutableList()
    }

    fun setRecent(settings: SettingsAPI, stickers: List<Sticker>) {
        settings.setString(KEY_RECENT, stickers.toJsonArray().toString())
    }

    fun addRecent(settings: SettingsAPI, sticker: Sticker) {
        val stickers = getRecent(settings)
        stickers.removeAll { it.id == sticker.id }
        stickers.add(0, sticker)
        while (stickers.size > RECENT_LIMIT) {
            stickers.removeAt(stickers.lastIndex)
        }
        setRecent(settings, stickers)
    }

    fun removeRecentByPackId(settings: SettingsAPI, packId: String) {
        val stickers = getRecent(settings).filter { (it.stickerPackId ?: "") != packId }
        setRecent(settings, stickers)
    }

    fun getPacksFile(): File {
        return File(getPacksDir(), "stickerpacks.json")
    }

    fun getPacksDir(): File {
        val dir = File(Constants.BASE_PATH, "MoreStickers")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun exportToFile(settings: SettingsAPI, file: File = getPacksFile()): Int {
        val packs = getPacks(settings)
        file.writeText(packs.toJsonArray().toString())
        return packs.size
    }

    fun importFromFile(settings: SettingsAPI, file: File = getPacksFile()): Int {
        logger.debug("importFromFile: ${file.absolutePath}")
        if (!file.exists()) {
            logger.warn("Import source missing: ${file.absolutePath}")
            return 0
        }
        val importFiles: ArrayList<File> = if (file.isDirectory) {
            logger.debug("Import source is directory")
            collectSupportedPackFiles(file)
        } else if (isSupportedPackFile(file)) {
            logger.debug("Import source is file: ${file.name}")
            ArrayList<File>().apply { add(file) }
        } else {
            logger.warn("Unsupported import source: ${file.absolutePath}")
            ArrayList()
        }
        logger.debug("Import files found: ${importFiles.size}")
        val packs = ArrayList<StickerPack>()
        for (index in 0 until importFiles.size) {
            val source = importFiles[index]
            try {
                logger.debug("Parsing pack file: ${source.absolutePath}")
                val parsed = parsePackFile(source)
                logger.debug("Parsed ${parsed.size} pack(s) from ${source.name}")
                for (packIndex in 0 until parsed.size) {
                    packs.add(parsed[packIndex])
                }
            } catch (e: Exception) {
                logger.error("Failed to parse ${source.absolutePath}", e)
            }
        }
        logger.debug("Total parsed packs: ${packs.size}")
        val normalized = ArrayList<StickerPack>()
        for (index in 0 until packs.size) {
            normalized.add(normalizePack(packs[index]))
        }
        val existing = getPacks(settings)
        logger.debug("Existing packs: ${existing.size}")
        val merged = mergeById(existing, normalized)
        logger.debug("Merged packs: ${merged.size}")
        setPacks(settings, merged)
        return normalized.size
    }

    private fun parsePackFile(file: File): List<StickerPack> {
        val text = file.readText().trimStart('\uFEFF').trim()
        return when {
            text.startsWith("[") -> text.toStickerPackList()
            text.startsWith("{") -> ArrayList<StickerPack>().apply { add(text.toStickerPack()) }
            else -> ArrayList<StickerPack>()
        }
    }

    private fun collectSupportedPackFiles(dir: File): ArrayList<File> {
        val files = ArrayList<File>()
        collectSupportedPackFiles(dir, files)
        logger.debug("Supported pack files collected: ${files.size}")
        return files
    }

    private fun collectSupportedPackFiles(dir: File, files: ArrayList<File>) {
        val children = dir.listFiles() ?: return
        logger.debug("Scanning dir: ${dir.absolutePath} (${children.size} entries)")
        for (index in children.indices) {
            val child = children[index]
            if (child.isDirectory) {
                logger.debug("Enter dir: ${child.absolutePath}")
                collectSupportedPackFiles(child, files)
            } else if (child.isFile && isSupportedPackFile(child)) {
                logger.debug("Pack file match: ${child.absolutePath}")
                files.add(child)
            } else {
                logger.debug("Skip file: ${child.absolutePath}")
            }
        }
    }

    fun downloadStickerToFile(sticker: Sticker, cacheDir: File): File {
        val url = sticker.image
        val baseName = sticker.filename ?: run {
            val afterSlash = if (url.contains('/')) url.substring(url.lastIndexOf('/') + 1) else url
            val beforeQuestion = if (afterSlash.contains('?')) afterSlash.substring(0, afterSlash.indexOf('?')) else afterSlash
            if (beforeQuestion.trim().isEmpty()) "sticker.png" else beforeQuestion
        }
        val safeName = ensureExtension(sanitizeFilename(baseName))
        val file = File(cacheDir, "more_stickers_${System.currentTimeMillis()}_$safeName")
        val response = Http.Request(url).execute()
        FileOutputStream(file).use { output ->
            response.pipe(output)
        }
        return file
    }

    private fun normalizePack(pack: StickerPack): StickerPack {
        val title = if (pack.title.trim().isEmpty()) pack.id else pack.title
        val fixedLogo = pack.logo?.let { logo ->
            logo.copy(stickerPackId = logo.stickerPackId ?: pack.id)
        }
        val fixedStickers = pack.stickers.map { sticker ->
            sticker.copy(stickerPackId = sticker.stickerPackId ?: pack.id)
        }
        return pack.copy(title = title, logo = fixedLogo, stickers = fixedStickers)
    }

    private fun mergeById(existing: List<StickerPack>, incoming: List<StickerPack>): List<StickerPack> {
        val merged = LinkedHashMap<String, StickerPack>()
        for (index in 0 until existing.size) {
            val pack = existing[index]
            merged[pack.id] = pack
        }
        for (index in 0 until incoming.size) {
            val pack = incoming[index]
            merged[pack.id] = pack
        }
        val result = ArrayList<StickerPack>(merged.size)
        for (entry in merged.values) {
            result.add(entry)
        }
        return result
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun ensureExtension(name: String): String {
        return if (name.contains('.')) name else "$name.png"
    }

    private fun isSupportedPackFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".json") || name.endsWith(".stickerpack")
    }

    @JvmName("packsToJsonArray")
    private fun List<StickerPack>.toJsonArray(): JSONArray = JSONArray(map { it.toJson() })

    @JvmName("stickersToJsonArray")
    private fun List<Sticker>.toJsonArray(): JSONArray = JSONArray(map { it.toJson() })

    private fun StickerPack.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            author?.let {
                put("author", JSONObject().apply {
                    put("name", it.name)
                    if (it.url != null) put("url", it.url)
                })
            }
            logo?.let { put("logo", it.toJson()) }
            put("stickers", stickers.toJsonArray())
        }
    }

    private fun Sticker.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("image", image)
            put("title", title)
            put("stickerPackId", stickerPackId)
            if (filename != null) put("filename", filename)
            if (isAnimated != null) put("isAnimated", isAnimated)
        }
    }

    private fun String.toStickerPackList(): List<StickerPack> {
        if (this.trim().isEmpty()) return emptyList()
        val array = JSONArray(this)
        return List(array.length()) { index -> array.getJSONObject(index).toStickerPack() }
    }

    private fun String.toStickerPack(): StickerPack = JSONObject(this).toStickerPack()

    private fun String.toStickerList(): List<Sticker> {
        if (this.trim().isEmpty()) return emptyList()
        val array = JSONArray(this)
        return List(array.length()) { index -> array.getJSONObject(index).toSticker() }
    }

    private fun JSONObject.toStickerPack(): StickerPack {
        val stickersArray = optJSONArray("stickers") ?: JSONArray()
        return StickerPack(
            id = optString("id"),
            title = optString("title"),
            author = optJSONObject("author")?.let { authorJson ->
                StickerAuthor(
                    name = authorJson.optString("name"),
                    url = authorJson.optString("url").takeIf { it.trim().isNotEmpty() },
                )
            },
            logo = optJSONObject("logo")?.toSticker(),
            stickers = List(stickersArray.length()) { index -> stickersArray.getJSONObject(index).toSticker() },
        )
    }

    private fun JSONObject.toSticker(): Sticker {
        return Sticker(
            id = optString("id"),
            image = optString("image"),
            title = optString("title"),
            stickerPackId = optString("stickerPackId").takeIf { it.trim().isNotEmpty() },
            filename = optString("filename").takeIf { it.trim().isNotEmpty() },
            isAnimated = if (has("isAnimated")) optBoolean("isAnimated") else null,
        )
    }
}
