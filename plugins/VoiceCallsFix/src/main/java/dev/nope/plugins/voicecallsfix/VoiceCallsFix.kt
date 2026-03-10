package dev.nope.plugins.voicecallsfix

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.InsteadHook
import com.aliucord.patcher.PreHook
import java.lang.reflect.Method
import okhttp3.WebSocket
import org.json.JSONArray
import org.json.JSONObject

/**
 * VoiceCallsFix - Fixes voice calls by implementing minimal DAVE protocol support.
 *
 * Root cause: Discord now requires DAVE (Discord Audio/Video E2EE) protocol support
 * for voice connections. Old Aliucord connects with voice gateway v5 and no DAVE
 * version, causing the server to reject with close code 4017.
 *
 * Strategy (Passthrough Mode):
 * 1. Upgrade voice gateway version from v5 to v7
 * 2. Inject max_dave_protocol_version: 1 into IDENTIFY payload
 * 3. Handle DAVE opcodes minimally (respond to transitions, skip real MLS crypto)
 * 4. Operate in passthrough mode (no actual E2EE, just like pre-DAVE Discord)
 *
 * Voice is still encrypted client-to-server (DTLS-SRTP) — just not end-to-end.
 */
@AliucordPlugin(requiresRestart = true)
class VoiceCallsFix : Plugin() {

    // Obfuscated class: b.a.q.n0.a = RtcControlSocket
    private val rtcControlSocketClass by lazy {
        Class.forName("b.a.q.n0.a")
    }

    companion object {
        const val VOICE_GATEWAY_VERSION = 7

        // DAVE protocol opcodes (not in old Aliucord's Opcodes.java)
        const val OP_CLIENTS_CONNECT = 11
        const val OP_DAVE_PROTOCOL_PREPARE_TRANSITION = 21
        const val OP_DAVE_PROTOCOL_EXECUTE_TRANSITION = 22
        const val OP_DAVE_PROTOCOL_READY_FOR_TRANSITION = 23
        const val OP_DAVE_PROTOCOL_PREPARE_EPOCH = 24
        const val OP_MLS_EXTERNAL_SENDER_PACKAGE = 25
        const val OP_MLS_KEY_PACKAGE = 26
        const val OP_MLS_PROPOSALS = 27
        const val OP_MLS_COMMIT_WELCOME = 28
        const val OP_MLS_ANNOUNCE_COMMIT_TRANSITION = 29
        const val OP_MLS_WELCOME = 30
        const val OP_MLS_INVALID_COMMIT_WELCOME = 31

        const val CLOSE_CODE_DAVE_REQUIRED = 4017
        const val DAVE_PROTOCOL_INIT_TRANSITION_ID = 0
    }

    override fun start(context: Context) {
        logger.info("VoiceCallsFix starting - patching voice gateway for DAVE protocol support")

        patchConnectMethod()
        patchIdentifyPayload()
        patchMessageHandler()
        patchCloseCodeHandler()

        logger.info("VoiceCallsFix patches applied successfully")
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        logger.info("VoiceCallsFix stopped")
    }

    // ── Patch 1: Replace e() to connect with v=7 instead of v=5 ────────────

    private fun patchConnectMethod() {
        try {
            val connectMethod = rtcControlSocketClass.getDeclaredMethod("e")
            connectMethod.isAccessible = true

            val fieldD = rtcControlSocketClass.getDeclaredField("D").apply { isAccessible = true }
            val fieldG = rtcControlSocketClass.getDeclaredField("G").apply { isAccessible = true }
            val fieldM = rtcControlSocketClass.getDeclaredField("m").apply { isAccessible = true }
            val fieldS = rtcControlSocketClass.getDeclaredField("s").apply { isAccessible = true }
            val fieldA = rtcControlSocketClass.getDeclaredField("A").apply { isAccessible = true }
            val fieldI = rtcControlSocketClass.getDeclaredField("I").apply { isAccessible = true }
            val fieldZ = rtcControlSocketClass.getDeclaredField("z").apply { isAccessible = true }
            val fieldO = rtcControlSocketClass.getDeclaredField("o").apply { isAccessible = true }

            val logInfoMethod = rtcControlSocketClass.getDeclaredMethod("i", String::class.java)
            logInfoMethod.isAccessible = true

            val closeMethod = rtcControlSocketClass.getDeclaredMethod(
                "b",
                kotlin.jvm.functions.Function1::class.java,
            )
            closeMethod.isAccessible = true

            val innerClassF = Class.forName("b.a.q.n0.a\$f")

            patcher.patch(connectMethod, InsteadHook { param ->
                val self = param.thisObject
                val endpoint = fieldD.get(self) as String
                val clock = fieldI.get(self)

                logInfoMethod.invoke(self, "[CONNECT] $endpoint")

                // If websocket already exists, close it
                if (fieldS.get(self) != null) {
                    val loggerObj = fieldG.get(self)
                    val tag = fieldM.get(self) as String
                    val eMethod = loggerObj.javaClass.getMethod(
                        "e",
                        String::class.java,
                        String::class.java,
                        Throwable::class.java,
                        java.util.Map::class.java,
                    )
                    eMethod.invoke(
                        loggerObj,
                        tag,
                        "Connect called with already existing websocket",
                        null,
                        null,
                    )
                    val fInstance =
                        innerClassF.getDeclaredField("j").apply { isAccessible = true }.get(null)
                    closeMethod.invoke(self, fInstance)
                    return@InsteadHook null
                }

                // Set connection start time
                val currentTimeMillisMethod = clock.javaClass.getMethod("currentTimeMillis")
                fieldA.set(self, currentTimeMillisMethod.invoke(clock) as Long)

                // Cancel existing timeout timer, schedule new 20s timeout
                val existingTimer = fieldZ.get(self) as? java.util.TimerTask
                existingTimer?.cancel()

                val timerTaskClass = Class.forName("b.a.q.n0.a\$g")
                val timerTaskCtor = timerTaskClass.getDeclaredConstructors()[0]
                timerTaskCtor.isAccessible = true
                val newTimerTask = timerTaskCtor.newInstance(self) as java.util.TimerTask
                fieldZ.set(self, newTimerTask)
                (fieldO.get(self) as java.util.Timer).schedule(newTimerTask, 20000L)

                // Build OkHttp client with 1-minute ping interval
                // Skip custom SSL setup — WSS uses TLS by default which works fine
                val builderClass = Class.forName("f0.x\$a")
                val builder = builderClass.getDeclaredConstructor().newInstance()

                val pingMethod = builderClass.getDeclaredMethod(
                    "a",
                    Long::class.javaPrimitiveType,
                    java.util.concurrent.TimeUnit::class.java,
                )
                pingMethod.isAccessible = true
                pingMethod.invoke(builder, 1L, java.util.concurrent.TimeUnit.MINUTES)

                // Build URL with v=7 instead of v=5
                val url = "${endpoint}?v=$VOICE_GATEWAY_VERSION"
                logInfoMethod.invoke(self, "attempting WSS connection with $url")

                val okhttpClientClass = Class.forName("f0.x")
                val client =
                    okhttpClientClass.getDeclaredConstructor(builderClass).newInstance(builder)

                // Build Request
                val reqBuilderClass = Class.forName("okhttp3.Request\$a")
                val reqBuilder = reqBuilderClass.getDeclaredConstructor().newInstance()
                val urlSetMethod = reqBuilderClass.getDeclaredMethod("f", String::class.java)
                urlSetMethod.isAccessible = true
                urlSetMethod.invoke(reqBuilder, url)
                val buildMethod = reqBuilderClass.getDeclaredMethod("a")
                buildMethod.isAccessible = true
                val request = buildMethod.invoke(reqBuilder)

                // Create WebSocket
                val newWsMethod = okhttpClientClass.getDeclaredMethod(
                    "g",
                    Class.forName("okhttp3.Request"),
                    Class.forName("okhttp3.WebSocketListener"),
                )
                newWsMethod.isAccessible = true
                val ws = newWsMethod.invoke(client, request, self)
                fieldS.set(self, ws)

                null
            })

            logger.info("Patched voice gateway URL to v=$VOICE_GATEWAY_VERSION")
        } catch (e: Exception) {
            logger.error("Failed to patch voice gateway version", e)
        }
    }

    // ── Patch 2: Inject max_dave_protocol_version into IDENTIFY ────────────
    //
    // Uses a PreHook on n(int, Object) — the send-opcode method.
    // For opcode 0 (IDENTIFY): serialize data manually with JSONObject,
    //   add max_dave_protocol_version, send directly, skip original.
    // For all other opcodes: let original run untouched.
    //
    // This avoids reflecting on Gson.toJson() which R8 may have stripped.

    private fun patchIdentifyPayload() {
        try {
            val sendMethod = rtcControlSocketClass.getDeclaredMethod(
                "n",
                Int::class.javaPrimitiveType,
                Object::class.java,
            )
            sendMethod.isAccessible = true

            val fieldWs = rtcControlSocketClass.getDeclaredField("s").apply { isAccessible = true }
            val logDebug = rtcControlSocketClass.getDeclaredMethod("h", String::class.java)
            logDebug.isAccessible = true

            patcher.patch(sendMethod, PreHook { param ->
                val opcode = param.args[0] as Int

                // Only intercept IDENTIFY (opcode 0); let all others pass through
                if (opcode != 0) return@PreHook

                val self = param.thisObject
                val identifyData = param.args[1]
                val ws = fieldWs.get(self) ?: return@PreHook // null ws → let original handle error

                try {
                    // Build IDENTIFY JSON with correct wire-format field names
                    val dataJson = identifyToJson(identifyData)

                    // Inject DAVE protocol version
                    dataJson.put("max_dave_protocol_version", 1)

                    // Build the complete outgoing message: {"op":0,"d":{...}}
                    val outgoing = JSONObject()
                    outgoing.put("op", 0)
                    outgoing.put("d", dataJson)

                    val json = outgoing.toString()
                    logger.info("Sending IDENTIFY with DAVE: $json")
                    logDebug.invoke(self, "sending (IDENTIFY): $json")

                    // Send via WebSocket — method is a(String) in obfuscated OkHttp
                    val wsSend = findWsSendMethod(ws)
                    wsSend.invoke(ws, json)

                    // Skip the original n() for this call
                    param.result = null
                } catch (e: Exception) {
                    logger.error("Failed to build IDENTIFY with DAVE, falling back to original", e)
                    // Don't set param.result — let original n() run (no DAVE but at least sends)
                }
            })

            logger.info("Patched IDENTIFY payload to include DAVE protocol version")
        } catch (e: Exception) {
            logger.error("Failed to patch IDENTIFY payload", e)
        }
    }

    /**
     * Find the WebSocket text send method. OkHttp's send(String) is obfuscated to a(String).
     */
    private fun findWsSendMethod(ws: Any): Method {
        return try {
            ws.javaClass.getMethod("a", String::class.java)
        } catch (e: NoSuchMethodException) {
            ws.javaClass.getMethod("send", String::class.java)
        }
    }

    /**
     * Converts a Payloads.Identify object to JSON with correct wire-format field names.
     * The Kotlin field names (serverId, sessionId, userId) differ from the wire names
     * (server_id, session_id, user_id) via @SerializedName annotations which are
     * obfuscated and hard to read via reflection. So we hardcode the known mappings.
     * Null values are omitted (matching Gson's default behavior).
     */
    private fun identifyToJson(identify: Any): JSONObject {
        val clazz = identify.javaClass
        val json = JSONObject()

        // Map Kotlin field names → wire-format JSON key names
        val fieldMappings = mapOf(
            "serverId" to "server_id",
            "userId" to "user_id",
            "sessionId" to "session_id",
            "token" to "token",
            "video" to "video",
            "streams" to "streams",
        )

        for ((fieldName, jsonKey) in fieldMappings) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                val value = field.get(identify) ?: continue // skip nulls
                when (jsonKey) {
                    "streams" -> {
                        @Suppress("UNCHECKED_CAST")
                        val streams = value as? List<Any> ?: continue
                        val arr = JSONArray()
                        for (stream in streams) {
                            arr.put(streamToJson(stream))
                        }
                        json.put(jsonKey, arr)
                    }
                    else -> json.put(jsonKey, value)
                }
            } catch (e: Exception) {
                logger.error("Failed to read Identify field: $fieldName", e)
            }
        }

        return json
    }

    /**
     * Converts a Payloads.Stream object to JSON with correct wire-format field names.
     * Null values are omitted.
     */
    private fun streamToJson(stream: Any): JSONObject {
        val clazz = stream.javaClass
        val json = JSONObject()

        val fieldMappings = mapOf(
            "type" to "type",
            "rid" to "rid",
            "quality" to "quality",
            "active" to "active",
            "ssrc" to "ssrc",
            "rtxSsrc" to "rtx_ssrc",
            "maxBitrate" to "max_bitrate",
            "maxFrameRate" to "max_framerate",
            "maxResolution" to "max_resolution",
        )

        for ((fieldName, jsonKey) in fieldMappings) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                val value = field.get(stream) ?: continue // skip nulls
                json.put(jsonKey, value)
            } catch (_: NoSuchFieldException) {
                // Field doesn't exist in this version, skip
            } catch (e: Exception) {
                logger.error("Failed to read Stream field: $fieldName", e)
            }
        }

        return json
    }

    // ── Patch 3: Handle DAVE opcodes in onMessage ──────────────────────────

    private fun patchMessageHandler() {
        try {
            val onMessageMethod = rtcControlSocketClass.getDeclaredMethod(
                "onMessage",
                WebSocket::class.java,
                String::class.java,
            )

            val sendMethod = rtcControlSocketClass.getDeclaredMethod(
                "n",
                Int::class.javaPrimitiveType,
                Object::class.java,
            )
            sendMethod.isAccessible = true

            patcher.patch(onMessageMethod, PreHook { param ->
                val self = param.thisObject
                val text = param.args[1] as String

                try {
                    val root = JSONObject(text)
                    val opcode = root.optInt("op", -1)
                    if (opcode == -1) return@PreHook

                    when (opcode) {
                        // SELECT_PROTOCOL_ACK (4) — log dave_protocol_version if present
                        4 -> {
                            val data = root.optJSONObject("d")
                            if (data != null && data.has("dave_protocol_version")) {
                                val version = data.getInt("dave_protocol_version")
                                logger.info("Server confirmed DAVE protocol version: $version")
                                if (version > 0) {
                                    logger.info(
                                        "DAVE protocol active, operating in passthrough mode",
                                    )
                                }
                            }
                            return@PreHook // Let original handler process the rest
                        }

                        // DAVE_PROTOCOL_PREPARE_TRANSITION (21)
                        OP_DAVE_PROTOCOL_PREPARE_TRANSITION -> {
                            val data = root.optJSONObject("d")
                            val transitionId = data?.optInt("transition_id", 0) ?: 0
                            val protoVer = data?.optInt("protocol_version", 0) ?: 0
                            logger.info(
                                "DAVE prepare transition: id=$transitionId, version=$protoVer",
                            )

                            if (transitionId == DAVE_PROTOCOL_INIT_TRANSITION_ID) {
                                logger.info("DAVE init transition (id=0), executing immediately")
                            } else {
                                sendDaveReadyForTransition(self, sendMethod, transitionId)
                            }

                            param.result = null
                            return@PreHook
                        }

                        // DAVE_PROTOCOL_EXECUTE_TRANSITION (22)
                        OP_DAVE_PROTOCOL_EXECUTE_TRANSITION -> {
                            val data = root.optJSONObject("d")
                            val transitionId = data?.optInt("transition_id", 0) ?: 0
                            logger.info("DAVE execute transition: id=$transitionId")
                            param.result = null
                            return@PreHook
                        }

                        // DAVE_PROTOCOL_PREPARE_EPOCH (24)
                        OP_DAVE_PROTOCOL_PREPARE_EPOCH -> {
                            val data = root.optJSONObject("d")
                            val epoch = data?.optInt("epoch", 0) ?: 0
                            val protoVer = data?.optInt("protocol_version", 0) ?: 0
                            logger.info("DAVE prepare epoch: epoch=$epoch, version=$protoVer")
                            param.result = null
                            return@PreHook
                        }

                        // MLS opcodes — consume silently in passthrough mode
                        OP_MLS_EXTERNAL_SENDER_PACKAGE,
                        OP_MLS_PROPOSALS,
                        OP_MLS_ANNOUNCE_COMMIT_TRANSITION,
                        OP_MLS_WELCOME,
                        OP_MLS_INVALID_COMMIT_WELCOME,
                        -> {
                            logger.info("Consumed MLS opcode $opcode (passthrough mode)")
                            param.result = null
                            return@PreHook
                        }

                        // CLIENTS_CONNECT (11) — log and let pass
                        OP_CLIENTS_CONNECT -> {
                            val data = root.optJSONObject("d")
                            logger.info("Clients connected: ${data?.optJSONArray("user_ids")}")
                            return@PreHook
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error processing incoming message for DAVE opcodes", e)
                }
            })

            logger.info("Patched message handler for DAVE opcodes")
        } catch (e: Exception) {
            logger.error("Failed to patch message handler", e)
        }
    }

    /** Send DAVE_PROTOCOL_READY_FOR_TRANSITION (op 23) */
    private fun sendDaveReadyForTransition(socket: Any, sendMethod: Method, transitionId: Int) {
        try {
            val payload = mapOf("transition_id" to transitionId)
            sendMethod.invoke(socket, OP_DAVE_PROTOCOL_READY_FOR_TRANSITION, payload)
            logger.info("Sent DAVE ready for transition: id=$transitionId")
        } catch (e: Exception) {
            logger.error("Failed to send DAVE ready for transition", e)
        }
    }

    // ── Patch 4: Handle close code 4017 gracefully ─────────────────────────

    private fun patchCloseCodeHandler() {
        try {
            val closeMethod = rtcControlSocketClass.getDeclaredMethod(
                "a",
                rtcControlSocketClass,
                Boolean::class.javaPrimitiveType,
                Integer::class.java,
                String::class.java,
            )
            closeMethod.isAccessible = true

            patcher.patch(closeMethod, PreHook { param ->
                val code = param.args[2] as? Int
                if (code == CLOSE_CODE_DAVE_REQUIRED) {
                    logger.warn(
                        "Received close code 4017 (DAVE required) — " +
                            "our DAVE patches should handle this on reconnect",
                    )
                }
            })

            logger.info("Patched close code handler for 4017")
        } catch (e: Exception) {
            logger.error("Failed to patch close code handler", e)
        }
    }
}
