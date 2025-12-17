import MyHttpClient.Companion.client
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.util.*

fun main() = runBlocking {

    // ===== MCP INIT =====
    val mcpProcess = ProcessBuilder("java", "-jar", "/home/igor/IdeaProjects/ig-mcp-server/build/libs/ig-mcp-server.jar")
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    val mcpTransport = StdioClientTransport(
        input = mcpProcess.inputStream.asInput(),
        output = mcpProcess.outputStream.asSink().buffered()
    )

    val mcpClient = Client(
        clientInfo = Implementation(
            name = "ig-mcp-client",
            version = "1.0.0"
        )
    )

    mcpClient.connect(mcpTransport)

    val mcpService = McpService(mcpClient)
    val mcpTools = mcpService.listTools()

    // Загружаем последний диалог при старте
    var conversation = ConversationStorage.loadLastConversation() ?: Conversation()

    println("🤖 AI Агент с сохранением истории в JSON")
    println("📂 Текущий диалог: ${conversation.id}")
    println("💾 Сохранено сообщений: ${conversation.messages.size}")
    println("Введите ваш запрос (или 'выход' для завершения):")

    var counter = 0
    val compressionThreshold = 10

    while (true) {
        print("> ")
        val userInput = readlnOrNull() ?: ""

        if (userInput.equals("выход", ignoreCase = true)) {
            break
        }

        // Добавляем сообщение пользователя
        conversation.messages.add(
            Message(role = Message.Role.USER, content = userInput)
        )

        // Сохраняем после добавления вопроса
        conversation.saveToFile()

        // Подготавливаем ВСЮ историю для LLM
        val messagesForLLM = prepareMessagesForLLM(conversation, mcpTools)
        println("📤 Отправка ${messagesForLLM.size} сообщений в LLM...")
        // Отладочный вывод первого сообщения
        if (messagesForLLM.isNotEmpty() && messagesForLLM[0].first == "system") {
            println("📋 Системный промпт: ${messagesForLLM[0].second}...")
        }

        // Отправляем в LLM с историей
        val llmAnswer = sendToLLM(messagesForLLM)

        val toolCall = extractToolCall(llmAnswer)

        val finalAnswer =
            if (toolCall != null) {
                println("🛠 MCP вызов: ${toolCall.tool}")
                val toolResult = mcpService.callTool(toolCall.tool, toolCall.arguments)

                sendToLLM(
                    messagesForLLM +
                            listOf(
                                "assistant" to llmAnswer,
                                "user" to "Результат инструмента:\n$toolResult\n\nСформулируй финальный ответ."
                            )
                )
            } else {
                llmAnswer
            }

        // Добавляем ответ ассистента
        conversation.messages.add(
            Message(role = Message.Role.ASSISTANT, content = finalAnswer)
        )

        // Сохраняем после получения ответа
        conversation.saveToFile()

        println("-".repeat(50))
        println("🤖 $finalAnswer")
        println("-".repeat(50))

        counter++

        if (counter == compressionThreshold) {
            counter = 0
            println("⚡ Компрессия диалога...")
            compressDialog(conversation)
            // После компрессии сохраняем обновленный диалог
            conversation.saveToFile()
        }
    }
    mcpClient.close()
    mcpProcess.destroy()

    client.close()
    println("👋 Программа завершена. Диалог сохранен в: conversations/${conversation.id}.json")
}

/**
 * Подготавливает сообщения для отправки в LLM
 */
private fun prepareMessagesForLLM(
    conversation: Conversation,
    mcpTools: List<String>
): List<Pair<String, String>> {

    val result = mutableListOf<Pair<String, String>>()
    val systemMessages = mutableListOf<String>()

    systemMessages.add(
        """
        Вы — AI ассистент GigaChat. Отвечайте на русском языке.
        """.trimIndent()
    )

    if (mcpTools.isNotEmpty()) {
        systemMessages.add(
            buildString {
                appendLine("У вас есть доступные инструменты:")
                mcpTools.forEach { appendLine("- $it") }
                appendLine()
                appendLine(
                    """
                    Если для ответа нужен инструмент,
                    ответьте СТРОГО в формате JSON:

                    {
                      "tool_call": {
                        "tool": "имя_инструмента",
                        "arguments": { ... }
                      }
                    }

                    Без любого другого текста.
                    """.trimIndent()
                )
            }
        )
    }

    conversation.messages
        .filter { it.role == Message.Role.SUMMARY }
        .forEach {
            systemMessages.add("Контекст: ${it.content}")
        }

    result.add("system" to systemMessages.joinToString("\n\n"))

    conversation.messages
        .filter { it.role == Message.Role.USER || it.role == Message.Role.ASSISTANT }
        .forEach {
            result.add(
                when (it.role) {
                    Message.Role.USER -> "user" to it.content
                    Message.Role.ASSISTANT -> "assistant" to it.content
                    else -> return@forEach
                }
            )
        }

    return result
}

/**
 * Функция компрессии диалога
 */
private suspend fun compressDialog(conversation: Conversation) {
    val messagesToCompress = conversation.messages
        .filter { it.role == Message.Role.USER || it.role == Message.Role.ASSISTANT }
        .takeLast(10)

    if (messagesToCompress.isEmpty()) return

    // Создаем промпт для суммаризации
    val summaryPrompt = buildString {
        appendLine("Пожалуйста, суммаризируйте следующий диалог, сохраняя ключевые детали:")
        messagesToCompress.forEach { message ->
            val roleName = when (message.role) {
                Message.Role.USER -> "Пользователь"
                Message.Role.ASSISTANT -> "Ассистент"
                else -> "Система"
            }
            appendLine("$roleName: ${message.content}")
        }
        appendLine("\nСуммаризация должна быть краткой, но сохранять контекст для продолжения диалога.")
    }

    // Вызываем LLM для суммаризации с пустой историей
    val summary = sendToLLM(listOf("user" to summaryPrompt))

    // Удаляем сжатые сообщения и добавляем суммаризацию
    conversation.messages.removeAll(messagesToCompress)
    conversation.messages.add(
        Message(
            role = Message.Role.SUMMARY,
            content = "Сжато ${messagesToCompress.size} сообщений: $summary"
        )
    )

    println("✅ Сжато ${messagesToCompress.size} сообщений")
    println("📝 Суммаризация: ${summary.take(100)}...")
}

/**
 * Ваша существующая функция sendToLLM
 */
private suspend fun sendToLLM(messages: List<Pair<String, String>>): String {
    try {
        val accessTokenResponse = client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append(HttpHeaders.Accept, "application/json")
                append("RqUID", UUID.randomUUID().toString()) // Генерируем новый каждый раз
                append(HttpHeaders.Authorization, "Basic $API_KEY")
            }
            setBody("scope=GIGACHAT_API_PERS")
        }.bodyAsText()

        val accessToken = jsonParser.decodeFromString<TokenAnswer>(accessTokenResponse).accessToken

        // Подготавливаем сообщения с историей
        val apiMessages = prepareApiMessages(messages)

//        // Отладочная информация
//        println("📨 Отправка в API:")
//        apiMessages.forEachIndexed { index, msg ->
//            println("  ${index + 1}. [${msg.role}] ${msg.content.take(50)}...")
//        }

        // Проверяем, что системное сообщение первое
        if (apiMessages.isEmpty() || apiMessages.first().role != "system") {
            println("❌ Ошибка: нет системного сообщения или оно не первое")
            return "Ошибка: системное сообщение должно быть первым"
        }

        // Создаем JSON для запроса
        val requestBody = jsonParser.encodeToString(
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

        // Парсим ответ с ignoreUnknownKeys
        val answerResponse = jsonParser.decodeFromString<ChatCompletionResponse>(response)

        // Проверяем на ошибки от API
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
 * ВАЖНО: системное сообщение должно быть только одно и первое
 */
private fun prepareApiMessages(messages: List<Pair<String, String>>): List<GigaChatMessage> {
    val apiMessages = mutableListOf<GigaChatMessage>()

    // Собираем все системные сообщения (включая SUMMARY)
    val allSystemContent = messages
        .filter { it.first == "system" }
        .joinToString("\n\n") { it.second }

    // Создаем одно системное сообщение со всем контентом
    apiMessages.add(
        GigaChatMessage(
            role = "system",
            content = allSystemContent.ifBlank {
                "Вы - полезный AI ассистент GigaChat. Отвечайте на русском языке."
            }
        ))

    // Затем добавляем все остальные сообщения (user, assistant)
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
    val status: Int? = null, // Добавляем поле status
    val message: String? = null // Добавляем поле message для ошибок
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


// ===== MCP SERVICE =====

class McpService(
    private val client: Client
) {
    suspend fun listTools(): List<String> =
        client.listTools().tools.map { it.name }

    suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any?>
    ): String {
        val result = client.callTool(toolName, arguments)
        return result.content.joinToString("\n") { it.toString() }
    }
}

// ===== TOOL CALL PARSING =====

@Serializable
data class ToolCallWrapper(
    @SerialName("tool_call")
    val toolCall: ToolCall
)

@Serializable
data class ToolCall(
    val tool: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

private val toolJson = Json { ignoreUnknownKeys = true }

private fun extractToolCall(text: String): ToolCall? =
    try {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}') + 1
        if (start < 0 || end <= start) null
        toolJson.decodeFromString<ToolCallWrapper>(text.substring(start, end)).toolCall
    } catch (_: Exception) {
        null
    }