import MyHttpClient.Companion.client
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

var currentDevice: String? = null


/**
 * Ваша существующая функция sendToLLM
 */
private suspend fun sendToLLM(messages: List<Pair<String, String>>): String {
    try {
        val accessTokenResponse = client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append(HttpHeaders.Accept, "application/json")
                append("RqUID", UUID.randomUUID().toString())
                append(HttpHeaders.Authorization, "Basic $API_KEY")
            }
            setBody("scope=GIGACHAT_API_PERS")
        }.bodyAsText()

        val accessToken = Json.decodeFromString<TokenAnswer>(accessTokenResponse).accessToken

        val apiMessages = prepareApiMessages(messages)

        val requestBody = Json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = MODEL,
                messages = apiMessages,
                max_tokens = 512,
                repetition_penalty = 1.0
            )
        )

        val response = client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.XRequestId, UUID.randomUUID().toString())
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(requestBody)
        }.bodyAsText()

        val answerResponse = Json.decodeFromString<ChatCompletionResponse>(response)

        if (answerResponse.status != null && answerResponse.status != 200) {
            return "Ошибка API (${answerResponse.status}): ${answerResponse.message ?: "Неизвестная ошибка"}"
        }

        val answer = answerResponse.choices?.first()?.message?.content
        val promptTokens = answerResponse.usage?.promptTokens
        val completionTokens = answerResponse.usage?.completionTokens
        val totalTokens = answerResponse.usage?.totalTokens

        if (totalTokens != null) {
            println("📊 Использовано токенов: $totalTokens (prompt: $promptTokens, completion: $completionTokens)")
        }

        return answer ?: "Не удалось получить ответ"

    } catch (e: Exception) {
        return "Ошибка: ${e.message}"
    }
}

/**
 * Конвертирует список пар (role, content) в формат для API GigaChat
 */
private fun prepareApiMessages(messages: List<Pair<String, String>>): List<GigaChatMessage> {
    val apiMessages = mutableListOf<GigaChatMessage>()

    val allSystemContent = messages
        .filter { it.first == "system" }
        .joinToString("\n\n") { it.second }

    apiMessages.add(
        GigaChatMessage(
            role = "system",
            content = allSystemContent.ifBlank {
                "Вы - полезный AI ассистент GigaChat. Отвечайте на русском языке."
            }
        ))

    messages
        .filter { it.first != "system" }
        .forEach { (role, content) ->
            apiMessages.add(
                GigaChatMessage(
                    role = role,
                    content = content
                )
            )
        }

    return apiMessages
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<GigaChatMessage>,
    val stream: Boolean = false,
    val max_tokens: Int = 512,
    val repetition_penalty: Double = 1.0
)

@Serializable
data class GigaChatMessage(
    val role: String,
    val content: String
)

const val API_KEY =
    "MDE5YWRiMzgtYWZjOS03MzRlLTk0MzEtYmE2YTI1N2E3ZDZkOmI2ZGZiYjYzLTFlNjUtNGU4Zi1iMzZhLTBjNjY0NDcxZmJjMg=="

const val MODEL = "GigaChat-2"

@Serializable
data class TokenAnswer(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_at")
    val expiresAt: Long,
)

@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val choices: List<Choice>? = null,
    val created: Long? = null,
    val model: String? = null,
    @SerialName("object")
    val objectType: String? = null,
    val usage: Usage? = null,
    val status: Int? = null,
    val message: String? = null
) {
    @Serializable
    data class Choice(
        @SerialName("finish_reason")
        val finishReason: String? = null,
        val index: Int? = null,
        val message: Message? = null
    ) {
        @Serializable
        data class Message(
            val content: String? = null,
            val role: String? = null
        )
    }

    @Serializable
    data class Usage(
        @SerialName("completion_tokens")
        val completionTokens: Int? = null,
        @SerialName("prompt_tokens")
        val promptTokens: Int? = null,
        @SerialName("system_tokens")
        val systemTokens: Int? = null,
        @SerialName("total_tokens")
        val totalTokens: Int? = null,
        @SerialName("precached_prompt_tokens")
        val precachedPromptTokens: Int? = null,
    )
}

fun main() = runBlocking {
    println("🚀 Starting Mobile MCP Agent")

    val process = ProcessBuilder()
        .command("npx", "-y", "@mobilenext/mobile-mcp@latest")
        .redirectErrorStream(true)
        .start()

    delay(2000)

    val transport = StdioClientTransport(
        input = process.inputStream.asInput(),
        output = process.outputStream.asSink().buffered()
    )

    val mcpClient = Client(
        clientInfo = Implementation(
            name = "android-agent",
            version = "1.0.0"
        )
    )

    mcpClient.connect(transport)
    val service = MobileMcpService(mcpClient)

    val tools = service.getAllTools()
    val systemPrompt = buildSystemPrompt(tools)

    println("✅ MCP connected")
    println("💬 Type a command in natural language (or 'exit')")

    while (true) {
        print("> ")
        val userInput = readln().trim()
        if (userInput == "exit") break

        val llmResponse = sendToLLM(
            systemPrompt = systemPrompt,
            userPrompt = userInput
        )

        println("🧠 LLM raw response:\n$llmResponse")

        val result = executeLlmToolCall(service, llmResponse)
        println("📱 Result:\n$result\n")
    }

    mcpClient.close()
    process.destroy()
}

/* ================= MCP SERVICE ================= */

class MobileMcpService(private val client: Client) {

    suspend fun getAllTools(): List<Tool> =
        client.listTools().tools

    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any?>
    ): String {
        val result = client.callTool(toolName, arguments)
        return result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text }
    }
}

/* ================= LLM ================= */
fun buildSystemPrompt(tools: List<Tool>): String =
    buildString {
        appendLine("Ты агент управления Android-устройством.")
        appendLine("Ты управляешь РЕАЛЬНЫМ устройством через MCP.")
        appendLine()
        appendLine("ДОСТУПНЫЕ ИНСТРУМЕНТЫ:")

        tools.forEach {
            appendLine()
            appendLine("Tool name: ${it.name}")
            appendLine("Description: ${it.description}")
            appendLine("Input schema: ${it.inputSchema}")
        }

        appendLine()
        appendLine("СТРОГИЕ ПРАВИЛА:")
        appendLine("1. Используй ТОЛЬКО инструменты из списка.")
        appendLine("2. НЕ придумывай названия инструментов.")
        appendLine("3. Если действие НЕ требуется, верни:")
        appendLine()
        appendLine(
            """
            {
              "tool": "none",
              "arguments": {}
            }
            """.trimIndent()
        )
        appendLine()
        appendLine("4. Если действие требуется, верни:")
        appendLine()
        appendLine(
            """
            {
              "tool": "<tool_name>",
              "arguments": { ... }
            }
            """.trimIndent()
        )
        appendLine()
        appendLine("5. НИКАКОГО текста вне JSON.")
    }

private suspend fun sendToLLM(
    systemPrompt: String,
    userPrompt: String
): String {
    try {
        // 1. Получаем access token
        val accessTokenResponse = client.post(
            "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
        ) {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append(HttpHeaders.Accept, "application/json")
                append("RqUID", UUID.randomUUID().toString())
                append(HttpHeaders.Authorization, "Basic $API_KEY")
            }
            setBody("scope=GIGACHAT_API_PERS")
        }.bodyAsText()

        val accessToken =
            Json.decodeFromString<TokenAnswer>(accessTokenResponse).accessToken

        // 2. Формируем сообщения
        val messages = listOf(
            GigaChatMessage(
                role = "system",
                content = systemPrompt
            ),
            GigaChatMessage(
                role = "user",
                content = userPrompt
            )
        )

        val requestBody = Json.encodeToString(
            ChatCompletionRequest.serializer(),
            ChatCompletionRequest(
                model = MODEL,
                messages = messages,
                max_tokens = 512,
                repetition_penalty = 1.0
            )
        )

        // 3. Запрос к GigaChat
        val response = client.post(
            "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
        ) {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.XRequestId, UUID.randomUUID().toString())
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(requestBody)
        }.bodyAsText()

        val answer =
            Json.decodeFromString<ChatCompletionResponse>(response)

        val content =
            answer.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()
                ?: error("Empty LLM response")

        // 4. ЖЁСТКАЯ проверка: ответ должен быть JSON
        if (!content.startsWith("{")) {
            error("LLM returned non-JSON response:\n$content")
        }

        return content

    } catch (e: Exception) {
        throw RuntimeException("LLM error: ${e.message}", e)
    }
}

/* ================= TOOL EXECUTOR ================= */

@Serializable
data class LlmToolCall(
    val tool: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

suspend fun executeLlmToolCall(
    service: MobileMcpService,
    llmResponse: String
): String {

    val call = Json.decodeFromString<LlmToolCall>(llmResponse)

    if (call.tool == "NO_ACTION") {
        return "ℹ️ No action required"
    }

    val tools = service.getAllTools()
    val tool = tools.find { it.name == call.tool }
        ?: return "❌ Unknown tool: ${call.tool}"

    val rawArgs = call.arguments.mapValues { jsonElementToAny(it.value) }
    val withDevice = injectDeviceIfNeeded(tool, rawArgs, currentDevice)
    val withDefaults = injectDefaults(tool, withDevice)
    val adaptedArgs = adaptArgumentsForTool(tool, withDefaults)

    if (call.tool == "mobile_list_available_devices") {
        val result = service.callTool(call.tool, adaptedArgs)
        currentDevice = extractFirstDeviceId(result)
        return result
    }

    return service.callTool(call.tool, adaptedArgs)
}

fun jsonElementToAny(value: JsonElement): Any? =
    when (value) {
        is JsonPrimitive -> {
            when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.boolean
                value.longOrNull != null -> value.long
                value.doubleOrNull != null -> value.double
                else -> null
            }
        }

        is JsonObject -> value.mapValues { jsonElementToAny(it.value) }
        is JsonArray -> value.map { jsonElementToAny(it) }
        else -> null
    }

fun injectDeviceIfNeeded(
    tool: Tool,
    args: Map<String, Any?>,
    device: String?
): Map<String, Any?> {
    if (device == null) return args
    val props = tool.inputSchema.properties?.jsonObject ?: return args
    return if ("device" in props && !args.containsKey("device")) {
        args + ("device" to device)
    } else args
}

fun adaptArgumentsForTool(
    tool: Tool,
    args: Map<String, Any?>
): Map<String, Any?> {

    val schemaProps =
        tool.inputSchema.properties?.jsonObject ?: return args

    // Если tool требует noParams и аргументы пустые
    if (
        "noParams" in schemaProps &&
        args.isEmpty()
    ) {
        return mapOf("noParams" to emptyMap<String, Any?>())
    }

    return args
}

fun extractFirstDeviceId(result: String): String? =
    try {
        val json = Json.parseToJsonElement(result).jsonObject
        json["devices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.content
    } catch (e: Exception) {
        null
    }

fun injectDefaults(
    tool: Tool,
    args: Map<String, Any?>
): Map<String, Any?> {
    val props = tool.inputSchema.properties?.jsonObject ?: return args

    var result = args

    if ("saveTo" in props && !result.containsKey("saveTo")) {
        result = result + ("saveTo" to "screenshot_${System.currentTimeMillis()}.png")
    }

    return result
}