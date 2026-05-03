package dev.nope.plugins.morestickers

data class StickerAuthor(
    val name: String,
    val url: String? = null,
)

data class Sticker(
    val id: String,
    val image: String,
    val title: String,
    val stickerPackId: String? = null,
    val filename: String? = null,
    val isAnimated: Boolean? = null,
)

data class StickerPack(
    val id: String,
    val title: String,
    val author: StickerAuthor? = null,
    val logo: Sticker? = null,
    val coverStickerId: Long? = null,
    val bannerAssetId: Long? = null,
    val stickers: List<Sticker> = emptyList(),
)
