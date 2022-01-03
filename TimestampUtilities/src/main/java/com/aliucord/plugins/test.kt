package com.aliucord.plugins

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import com.aliucord.entities.MessageEmbedBuilder
import com.discord.models.user.User
import java.util.*


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

// import java.util.*

// Aliucord Plugin annotation. Must be present on the main class of your plugin
@AliucordPlugin(requiresRestart = false /* Whether your plugin requires a restart after being installed/updated */)
// Plugin class. Must extend Plugin and override start and stop
// Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/1_introduction.md#basic-plugin-structure
class TimestampUtilities : Plugin() {



    override fun start(context: Context) {


        // Register a command with the name hello and description "My first command!" and no arguments.
        // Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/2_commands.md


        commands.registerCommand("timestamp", "Converts discord ID to date", listOf(
            Utils.createCommandOption(ApplicationCommandType.STRING, "timestamp", "Gimme timestamp", null, true, true)
        )) { ctx ->
            val id = ctx.getRequiredString("timestamp")
            val discordEpoch = 1420070400000
            val dateBits = id.toLong() shr 22
            val unix = (dateBits + discordEpoch)
            val unix2 = unix / 1000
            //val time = Date(unix) useless for now


                CommandsAPI.CommandResult("Message/User was created on <t:$unix2:F>. That's <t:$unix2:R>.", null, false)

        }

        commands.registerCommand("userinfo", "Hopefully gets info over a user idk", listOf(
            Utils.createCommandOption(ApplicationCommandType.STRING, "timestamp", "Gimme user timestamp", null, true, true)
        )) { ctx ->
            val userId = ctx.getRequiredString("timestamp")
            val userinfo: UserGlobalInfo = try {
                Http.Request.newDiscordRequest("/users/$userId")
                    .execute()
                    .json(UserGlobalInfo::class.java)


            } catch (throwable: Throwable) {
                logger.error(throwable)
                return@registerCommand CommandResult(
                    "Mission failed",
                    null,
                    false
                )
            }

            val discordEpoch = 1420070400000
            val dateBits = userId.toLong() shr 22
            val unix = (dateBits + discordEpoch)
            val unix2 = unix / 1000





            val embed: MessageEmbedBuilder =
                MessageEmbedBuilder().setRandomColor().setTitle("Username: " + userinfo.username + "#" + userinfo.discriminator).setImage("https://cdn.discordapp.com/avatars/${userId}/${userinfo.avatar}.webp?size=1024").setFooter("/userinfo | $userId").setDescription("Account created on <t:$unix2:F>")




            CommandResult("", Collections.singletonList(embed.build()), false)

        }



    }

    override fun stop(context: Context) {
        // Unregister our commands
        commands.unregisterAll()
    }


}
