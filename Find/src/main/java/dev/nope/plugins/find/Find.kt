package dev.nope.plugins.find

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI.CommandResult
import com.aliucord.entities.Plugin
import com.aliucord.wrappers.ChannelWrapper.Companion.guildId
import com.aliucord.wrappers.ChannelWrapper.Companion.parentId
import com.discord.api.commands.ApplicationCommandType
import com.discord.models.user.User
import com.discord.stores.StoreStream


@AliucordPlugin(requiresRestart = false)
class Find : Plugin() {

    private fun timestampToUnixTime(x: Long): Long {
        val discordEpoch = 1420070400000
        val dateBits = x shr 22
        val unixTimes1000 = (dateBits + discordEpoch)
        return unixTimes1000 / 1000
    }

    override fun start(context: Context) {
        commands.registerCommand(
            "find", "Tries fo find what a timestamp or a list of timestamps refers to", listOf(
                Utils.createCommandOption(
                    ApplicationCommandType.STRING,
                    "timestampList",
                    "Timestamps you want answers on. Separate with simple spaces. Later: separate with what you want",
                    null,
                    required = true,
                    default = true
                )
            )
        ) { ctx ->
            val ids = ctx.getRequiredString("timestampList")
            val input = findStringtoList(ids)
            val results: MutableMap<Long, String> = mutableMapOf()
            input.forEach {
                val colit: Collection<Long> = listOf(it)
                val tempUser: User? = StoreStream.getUsers().getUsers(colit, false)[it]
                val tempChannel = StoreStream.getChannels().getChannel(it)
                val tempServer = StoreStream.getGuilds().getGuild(it)
                //  val tempMessage = StoreStream.getMessages().getMe i need to test every channel lolilol
                try {
                    if (tempUser?.username == null) {
                        if (tempChannel?.guildId == null) {
                            if (tempServer?.id == null) {
                                results.put(it, "is neither a user, channel nor guild ID.")
                            } else {
                                results.put(it, "is a server.\nName: ${tempServer.name}.")
                            }
                        } else {
                            results.put(
                                it,
                                "is a channel: <#${it}> in category ${tempChannel.parentId} in server ${
                                    StoreStream.getGuilds().getGuild(tempChannel.guildId).name
                                }."
                            )
                        }
                    } else {
                        results.put(it, "is the user <@$it>.")
                    }
                } catch (throwable: Throwable) {
                    return@registerCommand CommandResult(
                        "oopsie doopsie, an error happened. Sorry ! Please check the logs !",
                        null,
                        false
                    )
                }

                if (results[it] == "Neither a user, channel nor guild ID.") { //Checks if the user exists. Should be able to be disabled later
                    val userinfo: UserGlobalInfo = try {
                        Http.Request.newDiscordRequest("/users/$it")
                            .execute()
                            .json(UserGlobalInfo::class.java)

                    } catch (throwable: Throwable) {
                        return@forEach
                    }
                    results[it] =
                        "is a user that was not cached. Name: ${userinfo.username}#${userinfo.discriminator}, created on <t:${
                            timestampToUnixTime(it)
                        }:F>. Avatar id: ${userinfo.avatar}"
                }
            }
            var finalList = ""
            results.forEach { (t, u) -> finalList += "\n**$t** $u" }

            CommandResult(
                finalList,
                null,
                false
            )
        }
    }


    private fun findStringtoList(ids: String): MutableList<Long> {
        val result = ids.split(" ").map { it.trim() }
        val result2: MutableList<String> = result as MutableList<String>
        val result3: MutableList<Long> = mutableListOf()
        val counter = 0
        result.forEach {
            try {
                it.toLong().takeIf { that -> that.toString().length in 17..19 } ?: (result2.set(
                    result.indexOf(it),
                    "0"
                ))
            } catch (throwable: Throwable) {
                result2[result.indexOf(it)] = "0"
            }
        }
        result2.forEach {
            if (it.toLong() != 0L) {
                result3.add(it.toLong())
            } else {
                counter + 1
            }

        }
        return result3
    }

    override fun stop(context: Context) {
        // Unregister our commands
        commands.unregisterAll()
    }
}
