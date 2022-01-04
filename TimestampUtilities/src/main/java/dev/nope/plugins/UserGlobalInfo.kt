package dev.nope.plugins

data class UserGlobalInfo(
    val accent_color: Int,
    val avatar: String,
    val banner: Any,
    val banner_color: String,
    val discriminator: String,
    val id: String,
    val public_flags: Int,
    val username: String
)