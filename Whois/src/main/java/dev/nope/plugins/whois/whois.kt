/*
 * Copyright (C) 2022 Vendicated & nope
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package dev.nope.plugins.whois

import android.content.Context
import android.graphics.Color
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.MessageEmbedBuilder
import com.aliucord.entities.Plugin
import com.aliucord.utils.RxUtils.await
import com.aliucord.wrappers.GuildRoleWrapper.Companion.permissions
import com.discord.api.commands.ApplicationCommandType
import com.discord.api.permission.Permission
import com.discord.api.user.UserFlags
import com.discord.api.user.UserProfile
import com.discord.models.member.GuildMember
import com.discord.models.user.CoreUser
import com.discord.stores.StoreStream
import com.discord.utilities.SnowflakeUtils
import com.discord.utilities.icon.IconUtils
import com.discord.utilities.rest.RestAPI
import com.discord.utilities.time.ClockFactory
import com.discord.utilities.time.TimeUtils
import com.discord.utilities.user.UserProfileUtilsKt
import com.discord.utilities.user.UserUtils
import java.util.*

fun Long.snowflakeToDateString() = SnowflakeUtils.toTimestamp(this).toDateString()
fun Long.toDateString(): String = TimeUtils.INSTANCE.toReadableTimeStringEN(Locale.ENGLISH, this, ClockFactory.get())

@AliucordPlugin
class UserLookup : Plugin() {
    private val flagToEmoji = mapOf(
        UserFlags.BUG_HUNTER_LEVEL_1 to "<:bughunter:585765206769139723>",
        UserFlags.BUG_HUNTER_LEVEL_2 to "<:goldbughunter:853274684337946648>",
        UserFlags.CERTIFIED_MODERATOR to "<:certifiedmod:853274382339670046>",
        UserFlags.HYPESQUAD_HOUSE1 to "<:bravery:585763004218343426>",
        UserFlags.HYPESQUAD_HOUSE2 to "<:brilliance:585763004495298575>",
        UserFlags.HYPESQUAD_HOUSE3 to "<:balance:585763004574859273>",
        UserFlags.PARTNER to "<:partnernew:754032603081998336>",
        UserFlags.STAFF to "<:stafftools:314348604095594498>",
        UserFlags.VERIFIED_DEVELOPER to "<:verifiedbotdev:853277205264859156>",
        UserFlags.PREMIUM_EARLY_SUPPORTER to "<:supporter:585763690868113455>",
    )

    override fun start(_ctx: Context) {
        val mentionOrId = arrayListOf(
            Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND, "user-id", "Look up a user by ID", subCommandOptions = arrayListOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.STRING, "id", "The id of the user to look up", required = true
                    )
                )
            ), Utils.createCommandOption(
                ApplicationCommandType.SUBCOMMAND, "mention", "Get info on a user by mention", subCommandOptions = arrayListOf(
                    Utils.createCommandOption(
                        ApplicationCommandType.USER, "user", "The user", required = true
                    )
                )
            )
        )

        commands.registerCommand("whois", "Look up a user", mentionOrId) { ctx ->
            val userId = when {
                ctx.containsArg("user-id") -> ctx.getRequiredSubCommandArgs("user-id")["id"]
                else -> ctx.getRequiredSubCommandArgs("mention")["user"]
            }.let {
                (it as String).toLongOrNull().takeIf { _ -> it.length in 17..19 } ?: return@registerCommand fail("Invalid input: $it")
            }
            val profile = getUserProfile(userId, ctx.currentChannel.guildId) ?: run {
                val user = RestAPI.api.userGet(userId).await().first ?: return@registerCommand fail("No such user")
                UserProfile(null, null, user, null, null, null, null)
            }
            val user = CoreUser(profile.g())


            val guildMember = profile.c()?.let { (GuildMember.Companion).from(it, ctx.currentChannel.guildId, emptyMap(), StoreStream.getGuilds()) }
            val embed = MessageEmbedBuilder().run {
                setAuthor(
                    "${if (user.isBot || user.isSystemUser) "\uD83E\uDD16 " else ""}${user.username}${
                        UserUtils.INSTANCE.getDiscriminatorWithPadding(
                            user
                        )
                    }"
                )
                IconUtils.INSTANCE.getForGuildMemberOrUser(
                    user, guildMember, 512, true
                ).let {
                    setThumbnail(it, it, 512, 512)
                }

                user.bannerColor?.let { setColor(Color.parseColor(it)) }
                user.banner?.let { hash ->
                    val icon = if (guildMember?.hasBanner() == true) IconUtils.INSTANCE.getForGuildMemberBanner(
                        guildMember.bannerHash, ctx.currentChannel.guildId, user.id, 2048, true
                    )
                    else IconUtils.INSTANCE.getForUserBanner(user.id, hash, 2048, true)
                    setImage(icon, icon, 818, 2048)
                }
                setDescription(user.bio)
                addField(
                    "Created At", user.id.snowflakeToDateString(), false
                )
                guildMember?.let {
                    addField("Joined At", it.joinedAt.g().toDateString(), true)
                    addField("Colour", "#${it.color.toString(16)}", true)

                    addField("Permissions", getPermissions(it.roles, ctx.currentChannel.guildId), true)
                }
                getBadgeEmojis(user.flags, profile).run {
                    if (isNotEmpty()) addField("Badges", this, true)
                }
                if (profile.d().isNotEmpty()) {
                    val guilds = StoreStream.getGuilds().guilds
                    val mutualGuilds = profile.d()
                    addField("Mutual Servers (${mutualGuilds.size})", mutualGuilds.joinToString("\n") { "• ${guilds[it.a()]!!.name}" }, true)
                }
                listOf(this.build())
            }
            CommandsAPI.CommandResult(null, embed, false)
        }


    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        commands.unregisterAll()
    }

    private fun getUserProfile(id: Long, guildId: Long?) = RestAPI.api.userProfileGet(id, true, guildId).await().first

    private fun getBadgeEmojis(flags: Int, profile: UserProfile) = StringBuilder().run {
        flagToEmoji.forEach { (flag, emoji) ->
            if (flags and flag != 0) append(emoji).append(' ')
        }
        if (UserProfileUtilsKt.isPremium(profile)) append("<:nitro:314068430611415041> ")
        UserProfileUtilsKt.getGuildBoostMonthsSubscribed(profile)?.let {
            append(
                when (it) {
                    0 -> "<:NitroBoost:699715144862662666>"
                    1 -> "<:booster:585764032162562058>"
                    2 -> "<:booster2:585764446253744128>"
                    3 -> "<:booster3:585764446220189716>"
                    else -> "<:booster4:585764446178246657>"
                }
            )
        }
        toString().trimEnd()
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun fail(msg: String) = CommandsAPI.CommandResult(msg, null, false)

    private fun getPermissions(roleIds: List<Long>, guildId: Long): String {
        val roles = StoreStream.getGuilds().roles[guildId]!!
        val perms = roleIds.fold(roles[guildId]!!.permissions) { acc, curr ->
            acc or roles[curr]!!.permissions
        }
        val re = "_\\w".toRegex()
        return Permission::class.java.declaredFields.mapNotNull { f ->
            when (f.name) {
                "DEFAULT", "ALL", "NONE", "ELEVATED", "MODERATOR_PERMISSIONS", "MANAGEMENT_PERMISSIONS" -> return@mapNotNull null
            }
            if (f.type == Long::class.java && perms and f.get(null) as Long != 0L)
                f.name.lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .replace(re) { " ${it.value[1].uppercase()}" }
            else null
        }.joinToString(", ")
    }

}

