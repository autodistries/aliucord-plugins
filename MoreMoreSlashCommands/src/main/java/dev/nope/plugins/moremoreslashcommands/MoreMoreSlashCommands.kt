package dev.nope.plugins.moremoreslashcommands
/*
 * Copyright (c) 2022  nope
 * Licensed under the Open Software License version 3.0
 */


import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.lytefast.flexinput.model.Attachment

@AliucordPlugin
@Suppress("unused")
class MoreSlashCommands : Plugin() {
    override fun start(context: Context?) {
       commands.registerCommand("fw", "Makes text full width", listOf(CommandsAPI.requiredMessageOption)) { ctx ->
            CommandsAPI.CommandResult(fullwidthify(ctx.getRequiredString("message").trim()))
        }

    }

    override fun stop(context: Context?) = commands.unregisterAll()


    private fun fullwidthify(text: String): String {
        return text
                .replace(" ", "　")
                .replace("!", "！")
                .replace("#", "＃")
                .replace("$", "＄")
                .replace("%", "％")
                .replace("&", "＆")
                .replace("'", "＇")
                .replace("(", "（")
                .replace(")", "）")
                .replace("*", "＊")
                .replace("+", "＋")
                .replace(",", "，")
                .replace("-", "－")
                .replace(".", "．")
                .replace("/", "／")
                .replace("0", "０")
                .replace("1", "１")
                .replace("2", "２")
                .replace("3", "３")
                .replace("4", "４")
                .replace("5", "５")
                .replace("6", "６")
                .replace("7", "７")
                .replace("8", "８")
                .replace("9", "９")
                .replace(":", "：")
                .replace(";", "；")
                .replace("<", "＜")
                .replace("=", "＝")
                .replace(">", "＞")
                .replace("?", "？")
                .replace("@", "＠")
                .replace("A", "Ａ")
                .replace("B", "Ｂ")
                .replace("C", "Ｃ")
                .replace("D", "Ｄ")
                .replace("E", "Ｅ")
                .replace("F", "Ｆ")
                .replace("G", "Ｇ")
                .replace("H", "Ｈ")
                .replace("I", "Ｉ")
                .replace("J", "Ｊ")
                .replace("K", "Ｋ")
                .replace("L", "Ｌ")
                .replace("M", "Ｍ")
                .replace("N", "Ｎ")
                .replace("O", "Ｏ")
                .replace("P", "Ｐ")
                .replace("Q", "Ｑ")
                .replace("R", "Ｒ")
                .replace("S", "Ｓ")
                .replace("T", "Ｔ")
                .replace("U", "Ｕ")
                .replace("V", "Ｖ")
                .replace("W", "Ｗ")
                .replace("X", "Ｘ")
                .replace("Y", "Ｙ")
                .replace("Z", "Ｚ")
                .replace("[", "［")
                .replace("]", "］")
                .replace("^", "＾")
                .replace("_", "＿")
                .replace("`", "｀")
                .replace("a", "ａ")
                .replace("b", "ｂ")
                .replace("c", "ｃ")
                .replace("d", "ｄ")
                .replace("e", "ｅ")
                .replace("f", "ｆ")
                .replace("g", "ｇ")
                .replace("h", "ｈ")
                .replace("i", "ｉ")
                .replace("j", "ｊ")
                .replace("k", "ｋ")
                .replace("l", "ｌ")
                .replace("m", "ｍ")
                .replace("n", "ｎ")
                .replace("o", "ｏ")
                .replace("p", "ｐ")
                .replace("q", "ｑ")
                .replace("r", "ｒ")
                .replace("s", "ｓ")
                .replace("t", "ｔ")
                .replace("u", "ｕ")
                .replace("v", "ｖ")
                .replace("w", "ｗ")
                .replace("x", "ｘ")
                .replace("y", "ｙ")
                .replace("z", "ｚ")
                .replace("{", "｛")
                .replace("|", "｜")
                .replace("}", "｝")
                .replace("~", "～")



    }
}
