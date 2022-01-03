package com.aliucord.plugins

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.discord.api.commands.ApplicationCommandType

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
            val userinfo: String = try {
                Http.Request.newDiscordRequest("/users/$userId")
                    .execute()
                    .text()

            } catch (throwable: Throwable) {
                logger.error(throwable)
                return@registerCommand CommandsAPI.CommandResult(
                    "Sowwyyy  i faiwed. >.<",
                    null,
                    false
                )
            }




            CommandsAPI.CommandResult("OwO youww uwer iw $userinfo", null, false)

        }

    }

    override fun stop(context: Context) {
        // Unregister our commands
        commands.unregisterAll()
    }
}


class Testclass(val info: String)
