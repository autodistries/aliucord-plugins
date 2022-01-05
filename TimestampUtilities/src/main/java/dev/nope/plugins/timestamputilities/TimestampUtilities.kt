package dev.nope.plugins.timestamputilities

import android.content.Context
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType


@AliucordPlugin(requiresRestart = false )
class TimestampUtilities : Plugin() {

    private fun timestampToUnixTime(x: Long): Long {
        val discordEpoch = 1420070400000
        val dateBits = x shr 22
        val unixTimes1000 = (dateBits + discordEpoch)
        return unixTimes1000 / 1000
    }

    override fun start(context: Context) {
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
            CommandResult(
                "Message, channel, server or user was created on <t:$unixTime:F>. That's <t:$unixTime:R>.",
                null,
                false
            )
        }
    }

    override fun stop(context: Context) {
        // Unregister our commands
        commands.unregisterAll()
    }
}
