package dev.nope.plugins

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType
import java.util.*


@AliucordPlugin(requiresRestart = false )
class TimestampUtilities : Plugin() {


    private fun timestampToUnixTime(x: Long): Long {
        val discordEpoch = 1420070400000
        val dateBits = x shr 22
        val unixTimes1000 = (dateBits + discordEpoch)
        return unixTimes1000 / 1000
        // val time = Date(unix) That is less precise and adapting to timezones is harder

    }

    override fun start(context: Context) {


        // Register a command with the name hello and description "My first command!" and no arguments.
        // Learn more: https://github.com/Aliucord/documentation/blob/main/plugin-dev/2_commands.md


        commands.registerCommand(
            "timestamp", "Converts discord ID to date", listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "timestamp",
                    "Gimme timestamp",
                    null,
                    required = true,
                    default = true
                )
            )
        ) { ctx ->
            val id = ctx.getRequiredString("timestamp")
            val unixTime = timestampToUnixTime(id.toLong())


            CommandsAPI.CommandResult(
                "Message/User was created on <t:$unixTime:F>. That's <t:$unixTime:R>.",
                null,
                false
            )

        }

        commands.registerCommand(
            "userinfo", "Gets basic info over any user", listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "timestamp",
                    "Gimme user timestamp",
                    null,
                    required = true,
                    default = true
                )
            )
        ) { ctx ->
            val userId = ctx.getRequiredString("timestamp")
            val userinfo: UserGlobalInfo = try {
                Http.Request.newDiscordRequest("/users/$userId")
                    .execute()
                    .json(UserGlobalInfo::class.java)


            } catch (throwable: Throwable) {
                logger.error(throwable)
                return@registerCommand CommandResult("Mission failed, sorry. Help by reporting that. Check the logs !", null, false)
            }


            val embed: MessageEmbedBuilder =
                MessageEmbedBuilder().setRandomColor()
                    .setTitle("Username: " + userinfo.username + "#" + userinfo.discriminator)
                    .setImage("https://cdn.discordapp.com/avatars/${userId}/${userinfo.avatar}.webp?size=2048")
                    .setFooter("/userinfo | $userId")
                    .setDescription("Account created on <t:${timestampToUnixTime(userId.toLong())}:F>")




            CommandResult("", Collections.singletonList(embed.build()), false)

        }


    }

    override fun stop(context: Context) {
        // Unregister our commands
        commands.unregisterAll()
    }


}
