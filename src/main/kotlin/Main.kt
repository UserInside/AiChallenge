import MyHttpClient.Companion.client
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*

fun main() = runBlocking {

    // ===== MCP INIT =====
    val mcpProcess =
        ProcessBuilder("java", "-jar", "/home/igor/IdeaProjects/ig-mcp-server/build/libs/ig-mcp-server.jar")
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

    // 🔥 ЗАПУСК ПЛАНИРОВЩИКА
    startRecipeScheduler(mcpService)

    // дальше — интерактивный режим или просто keep-alive
    delay(Long.MAX_VALUE)

//    // Загружаем последний диалог при старте
//    var conversation = ConversationStorage.loadLastConversation() ?: Conversation()
//
//    println("🤖 AI Агент с сохранением истории в JSON")
//    println("📂 Текущий диалог: ${conversation.id}")
//    println("💾 Сохранено сообщений: ${conversation.messages.size}")
//    println("Введите ваш запрос (или 'выход' для завершения):")
//
//    var counter = 0
//    val compressionThreshold = 10
//
//    while (true) {
//        print("> ")
//        val userInput = readlnOrNull() ?: ""
//
//        if (userInput.equals("выход", ignoreCase = true)) {
//            break
//        }
//
//        // Добавляем сообщение пользователя
//        conversation.messages.add(
//            Message(role = Message.Role.USER, content = userInput)
//        )
//
//        // Сохраняем после добавления вопроса
//        conversation.saveToFile()
//
//        // Подготавливаем ВСЮ историю для LLM
//        val messagesForLLM = prepareMessagesForLLM(conversation, mcpTools)
//        println("📤 Отправка ${messagesForLLM.size} сообщений в LLM...")
//        // Отладочный вывод первого сообщения
//        if (messagesForLLM.isNotEmpty() && messagesForLLM[0].first == "system") {
//            println("📋 Системный промпт: ${messagesForLLM[0].second}...")
//        }
//
//        // Отправляем в LLM с историей
//        val llmAnswer = sendToLLM(messagesForLLM)
//
//        val toolCall = extractToolCall(llmAnswer)
//
//        val finalAnswer =
//            if (toolCall != null) {
//                println("🛠 MCP вызов: ${toolCall.tool}")
//                val toolResult = mcpService.callTool(toolCall.tool, toolCall.arguments)
//
//                sendToLLM(
//                    messagesForLLM +
//                            listOf(
//                                "assistant" to llmAnswer,
//                                "user" to "Результат инструмента:\n$toolResult\n\nСформулируй финальный ответ."
//                            )
//                )
//            } else {
//                llmAnswer
//            }
//
//        // Добавляем ответ ассистента
//        conversation.messages.add(
//            Message(role = Message.Role.ASSISTANT, content = finalAnswer)
//        )
//
//        // Сохраняем после получения ответа
//        conversation.saveToFile()
//
//        println("-".repeat(50))
//        println("🤖 $finalAnswer")
//        println("-".repeat(50))
//
//        counter++
//
//        if (counter == compressionThreshold) {
//            counter = 0
//            println("⚡ Компрессия диалога...")
//            compressDialog(conversation)
//            // После компрессии сохраняем обновленный диалог
//            conversation.saveToFile()
//        }
//    }
//    mcpClient.close()
//    mcpProcess.destroy()
//
//    client.close()
//    println("👋 Программа завершена. Диалог сохранен в: conversations/${conversation.id}.json")
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
Если вопрос касается рецептов, еды или рекомендаций блюд —
ВЫ ОБЯЗАНЫ использовать один из инструментов.
Ответ БЕЗ tool_call считается ошибкой.

    Правила:
    1. Если вопрос касается рецептов, еды или рекомендаций блюд —
ВЫ ОБЯЗАНЫ использовать один из инструментов.
Ответ БЕЗ tool_call считается ошибкой.
    
    Пример:

Вопрос: Посоветуй случайный рецепт
Ответ:
{
  "tool_call": {
    "tool": "get_random_meal",
    "arguments": {}
  }
}

    В этом случае инструмент НЕ используется.
    2. Используйте инструмент ТОЛЬКО если требуется:
       - получить данные,
       - выполнить вычисление,
       - вызвать внешний сервис.
    3. Если используется инструмент, верните СТРОГО JSON без текста до или после:

    {
      "tool_call": {
        "tool": "<ИМЯ_ИНСТРУМЕНТА>",
        "arguments": { ... }
      }
    }

    Ограничения:
    - <ИМЯ_ИНСТРУМЕНТА> должно быть ТОЧНО одним из списка выше
    - если инструмент не нужен — НИКОГДА не возвращайте JSON
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

        return result.content
            .filterIsInstance<TextContent>()
            .joinToString("\n") { it.text }
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

private fun extractToolCall(text: String): ToolCall? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{")) return null


    return try {
        toolJson.decodeFromString<ToolCallWrapper>(trimmed).toolCall
    } catch (_: Exception) {
        null
    }
}

data class RecipeHistory(
    val lastMeals: MutableList<String> = mutableListOf()
)

const val MAX_HISTORY = 5

fun startRecipeScheduler(
    mcpService: McpService
) = CoroutineScope(Dispatchers.Default).launch {

    // 1. Загружаем историю ОДИН раз при старте агента
    val store = loadRecipes()

    while (isActive) {
        try {
            // 2. Получаем рецепт через MCP
            val mcpResult = mcpService.callTool(
                toolName = "get_random_meal",
                arguments = emptyMap()
            )


            // 3. Парсим ответ MCP → доменная модель
            val recipe = parseRecipe(mcpResult)

            // 4. Сохраняем в память
            store.recipes.add(recipe)

            // 5. Пишем на диск
            saveRecipes(store)

            // 6. Проверяем: есть ли 3 рецепта
            if (store.recipes.size >= 3) {
                val lastThree = store.recipes.takeLast(3)

                // 7. Генерируем summary через LLM
                val summary = buildSummary(lastThree)

                // 8. Уведомляем пользователя
                notifyUser(summary)
            }

        } catch (e: Exception) {
            println("❌ Ошибка планировщика: ${e.message}")
        }

        // 9. Пауза на 1 час
        delay(Duration.ofSeconds(5).toMillis())
    }
}

@Serializable
data class StoredRecipe(
    val id: String,
    val name: String,
    val area: String,
    val timestamp: String
)

@Serializable
data class RecipeStore(
    val recipes: MutableList<StoredRecipe> = mutableListOf()
)

private const val RECIPES_FILE = "recipes.json"

suspend fun buildSummary(
    recipes: List<StoredRecipe>
): String {

    val prompt = buildString {
        appendLine("Сделай краткий обзор следующих рецептов:")
        recipes.forEach {
            appendLine("- ${it.name} (${it.area})")
        }
        appendLine("Ответ — 3–5 предложений.")
    }

    return sendToLLM(
        listOf(
            "system" to "Ты кулинарный ассистент.",
            "user" to prompt
        )
    )
}

fun notifyUser(summary: String) {
    println("🍽 SUMMARY ПО ПОСЛЕДНИМ РЕЦЕПТАМ:")
    println(summary)
}

fun loadRecipes(): RecipeStore {
    val file = File(RECIPES_FILE)

    if (!file.exists()) {
        return RecipeStore()
    }

    return try {
        Json.decodeFromString(
            RecipeStore.serializer(),
            file.readText()
        )
    } catch (e: Exception) {
        println("⚠️ Ошибка чтения recipes.json, создаю новый файл")
        RecipeStore()
    }
}

fun saveRecipes(store: RecipeStore) {
    File(RECIPES_FILE).writeText(
        Json.encodeToString(RecipeStore.serializer(), store)
    )
}

@Serializable
data class MealsResponse(
    val meals: List<MealDto>? = null
)
//
//@Serializable
//data class MealDto(
//    @SerialName("idMeal")
//    val id: String,
//    @SerialName("strMeal")
//    val name: String,
//    @SerialName("strArea")
//    val area: String,
//    @SerialName("strInstruction")
//    val strInstruction: String
//)

fun parseRecipe(mcpResult: String): StoredRecipe {
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    val dto = json.decodeFromString<MealDto>(mcpResult)

    return StoredRecipe(
        id = dto.idMeal,
        name = dto.strMeal,
        area = dto.strArea,
        timestamp = Instant.now().toString()
    )
}


@Serializable
data class MealDto(
    @SerialName("idMeal")
    val idMeal: String,

    @SerialName("strMeal")
    val strMeal: String,

    @SerialName("strMealAlternate")
    val strMealAlternate: String? = null,

    @SerialName("strCategory")
    val strCategory: String? = null,

    @SerialName("strArea")
    val strArea: String,

    @SerialName("strInstructions")
    val strInstructions: String? = null,

    @SerialName("strMealThumb")
    val strMealThumb: String? = null,

    @SerialName("strTags")
    val strTags: String? = null,

    @SerialName("strYoutube")
    val strYoutube: String? = null,

    // Ингредиенты
    @SerialName("strIngredient1") val strIngredient1: String? = null,
    @SerialName("strIngredient2") val strIngredient2: String? = null,
    @SerialName("strIngredient3") val strIngredient3: String? = null,
    @SerialName("strIngredient4") val strIngredient4: String? = null,
    @SerialName("strIngredient5") val strIngredient5: String? = null,
    @SerialName("strIngredient6") val strIngredient6: String? = null,
    @SerialName("strIngredient7") val strIngredient7: String? = null,
    @SerialName("strIngredient8") val strIngredient8: String? = null,
    @SerialName("strIngredient9") val strIngredient9: String? = null,
    @SerialName("strIngredient10") val strIngredient10: String? = null,
    @SerialName("strIngredient11") val strIngredient11: String? = null,
    @SerialName("strIngredient12") val strIngredient12: String? = null,
    @SerialName("strIngredient13") val strIngredient13: String? = null,
    @SerialName("strIngredient14") val strIngredient14: String? = null,
    @SerialName("strIngredient15") val strIngredient15: String? = null,
    @SerialName("strIngredient16") val strIngredient16: String? = null,
    @SerialName("strIngredient17") val strIngredient17: String? = null,
    @SerialName("strIngredient18") val strIngredient18: String? = null,
    @SerialName("strIngredient19") val strIngredient19: String? = null,
    @SerialName("strIngredient20") val strIngredient20: String? = null,

    // Меры
    @SerialName("strMeasure1") val strMeasure1: String? = null,
    @SerialName("strMeasure2") val strMeasure2: String? = null,
    @SerialName("strMeasure3") val strMeasure3: String? = null,
    @SerialName("strMeasure4") val strMeasure4: String? = null,
    @SerialName("strMeasure5") val strMeasure5: String? = null,
    @SerialName("strMeasure6") val strMeasure6: String? = null,
    @SerialName("strMeasure7") val strMeasure7: String? = null,
    @SerialName("strMeasure8") val strMeasure8: String? = null,
    @SerialName("strMeasure9") val strMeasure9: String? = null,
    @SerialName("strMeasure10") val strMeasure10: String? = null,
    @SerialName("strMeasure11") val strMeasure11: String? = null,
    @SerialName("strMeasure12") val strMeasure12: String? = null,
    @SerialName("strMeasure13") val strMeasure13: String? = null,
    @SerialName("strMeasure14") val strMeasure14: String? = null,
    @SerialName("strMeasure15") val strMeasure15: String? = null,
    @SerialName("strMeasure16") val strMeasure16: String? = null,
    @SerialName("strMeasure17") val strMeasure17: String? = null,
    @SerialName("strMeasure18") val strMeasure18: String? = null,
    @SerialName("strMeasure19") val strMeasure19: String? = null,
    @SerialName("strMeasure20") val strMeasure20: String? = null,

    @SerialName("strSource")
    val strSource: String? = null,

    @SerialName("strImageSource")
    val strImageSource: String? = null,

    @SerialName("strCreativeCommonsConfirmed")
    val strCreativeCommonsConfirmed: String? = null,

    @SerialName("dateModified")
    val dateModified: String? = null
)
