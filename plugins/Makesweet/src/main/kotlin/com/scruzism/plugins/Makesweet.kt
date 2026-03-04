package com.scruzism.plugins

import android.content.Context
import android.net.Uri

import com.aliucord.Http
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.annotations.AliucordPlugin
import com.discord.api.commands.ApplicationCommandType

import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

private const val BASE_URL = "https://vps-04375e64.vps.ovh.net"

private fun buildReq(path: String, method: String = "POST"): Http.Request {
    return Http.Request(BASE_URL + path, method)
}

private fun makeTempFile(response: Http.Response, mContext: Context, ext: String = ".gif"): File {
    val tempFile = File.createTempFile("temp", ext, mContext.cacheDir)
    val os = FileOutputStream(tempFile)
    response.pipe(os)
    tempFile.deleteOnExit()
    return tempFile
}

private fun downloadImage(url: String, mContext: Context): File {
    val resp = Http.Request(url).execute()
    return makeTempFile(resp, mContext, ".png")
}

private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

@AliucordPlugin
class Makesweet : Plugin() {

    private var log = Logger("makesweet")

    override fun start(ctx: Context) {

        val args = listOf(
                // text: left text + optional right text
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "text",
                        "Text on both sides of the locket",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "leftText",
                                        "Text for the left side",
                                        required = true
                                ),
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "rightText",
                                        "Text for the right side (optional, defaults to left)",
                                        required = false
                                )
                        )
                ),
                // image: single image for both sides
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "image",
                        "Single image for both sides",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "imageUrl",
                                        "Image URL",
                                        required = true
                                )
                        )
                ),
                // textAndImage: text left, image right
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "textAndImage",
                        "Text on left, image on right",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "text",
                                        "Text for the left side",
                                        required = true
                                ),
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "imageUrl",
                                        "Image URL for the right side",
                                        required = true
                                )
                        )
                ),
                // imageAndText: image left, text right
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "imageAndText",
                        "Image on left, text on right",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "imageUrl",
                                        "Image URL for the left side",
                                        required = true
                                ),
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "text",
                                        "Text for the right side",
                                        required = true
                                )
                        )
                ),
                // imageAndImage: two different images
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "imageAndImage",
                        "Different images on each side",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "leftImageUrl",
                                        "Image URL for the left side",
                                        required = true
                                ),
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "rightImageUrl",
                                        "Image URL for the right side",
                                        required = true
                                )
                        )
                ),
                // textLeft: text only on left side
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "textLeft",
                        "Text only on the left side",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "text",
                                        "Text for the left side",
                                        required = true
                                )
                        )
                ),
                // textRight: text only on right side
                Utils.createCommandOption(
                        ApplicationCommandType.SUBCOMMAND,
                        "textRight",
                        "Text only on the right side",
                        subCommandOptions = listOf(
                                Utils.createCommandOption(
                                        ApplicationCommandType.STRING,
                                        "text",
                                        "Text for the right side",
                                        required = true
                                )
                        )
                )
        )

        commands.registerCommand("makesweet", "Heart locket animation generator", args) {
            try {
                val resp: Http.Response = when {
                    it.containsArg("text") -> {
                        val subArgs = it.getSubCommandArgs("text")!!
                        val leftText = enc(subArgs["leftText"].toString())
                        val rawRight = subArgs["rightText"]?.toString()
                        val url = StringBuilder("/heartlocket?left_text=$leftText")
                        if (rawRight != null && rawRight != "null") {
                            url.append("&right_text=${enc(rawRight)}")
                        }
                        buildReq(url.toString()).execute()
                    }

                    it.containsArg("textLeft") -> {
                        val subArgs = it.getSubCommandArgs("textLeft")!!
                        val text = enc(subArgs["text"].toString())
                        buildReq("/heartlocket?left_text=$text").execute()
                    }

                    it.containsArg("textRight") -> {
                        val subArgs = it.getSubCommandArgs("textRight")!!
                        val text = enc(subArgs["text"].toString())
                        buildReq("/heartlocket?right_text=$text").execute()
                    }

                    it.containsArg("image") -> {
                        val subArgs = it.getSubCommandArgs("image")!!
                        val imageUrl = subArgs["imageUrl"].toString()
                        val imageFile = downloadImage(imageUrl, ctx)
                        buildReq("/heartlocket").executeWithMultipartForm(
                                mapOf("left_image" to imageFile)
                        )
                    }

                    it.containsArg("textAndImage") -> {
                        val subArgs = it.getSubCommandArgs("textAndImage")!!
                        val text = enc(subArgs["text"].toString())
                        val imageUrl = subArgs["imageUrl"].toString()
                        val imageFile = downloadImage(imageUrl, ctx)
                        buildReq("/heartlocket?left_text=$text").executeWithMultipartForm(
                                mapOf("right_image" to imageFile)
                        )
                    }

                    it.containsArg("imageAndText") -> {
                        val subArgs = it.getSubCommandArgs("imageAndText")!!
                        val imageUrl = subArgs["imageUrl"].toString()
                        val text = enc(subArgs["text"].toString())
                        val imageFile = downloadImage(imageUrl, ctx)
                        buildReq("/heartlocket?right_text=$text").executeWithMultipartForm(
                                mapOf("left_image" to imageFile)
                        )
                    }

                    it.containsArg("imageAndImage") -> {
                        val subArgs = it.getSubCommandArgs("imageAndImage")!!
                        val leftFile = downloadImage(subArgs["leftImageUrl"].toString(), ctx)
                        val rightFile = downloadImage(subArgs["rightImageUrl"].toString(), ctx)
                        buildReq("/heartlocket").executeWithMultipartForm(
                                mapOf("left_image" to leftFile, "right_image" to rightFile)
                        )
                    }

                    else -> {
                        return@registerCommand CommandsAPI.CommandResult(
                                "Use a subcommand: text, image, textAndImage, imageAndText, imageAndImage, textLeft, textRight",
                                null,
                                false
                        )
                    }
                }

                val file = makeTempFile(resp, ctx)
                it.addAttachment(Uri.fromFile(file).toString(), "makesweet.gif")
                CommandsAPI.CommandResult("")
            } catch (e: Exception) {
                log.error("makesweet failed", e)
                CommandsAPI.CommandResult("Failed: ${e.message}", null, false)
            }
        }
    }

    override fun stop(ctx: Context) = commands.unregisterAll()
}
