import MyHttpClient.Companion.client
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.*
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.Instant
import java.util.*

fun main() = runBlocking {
    println("🚀 Запуск агента...")

    // ===== MCP INIT =====
    println("📦 Запуск MCP серверов...")

    val mcpProcessFindRecipe =
        ProcessBuilder("java", "-jar", "/home/igor/IdeaProjects/ig-mcp-server/build/libs/ig-mcp-server.jar")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    val mcpProcessSaveRecipe =
        ProcessBuilder("java", "-jar", "/home/igor/IdeaProjects/storageMcpServer/build/libs/storageMcpServer.jar")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    // Даём время серверам запуститься
    delay(2000)

    println("🔌 Подключение к MCP серверам...")

    val mcpTransportFindRecipe = StdioClientTransport(
        input = mcpProcessFindRecipe.inputStream.asInput(),
        output = mcpProcessFindRecipe.outputStream.asSink().buffered()
    )

    val mcpTransportSaveRecipe = StdioClientTransport(
        input = mcpProcessSaveRecipe.inputStream.asInput(),
        output = mcpProcessSaveRecipe.outputStream.asSink().buffered()
    )

    val mcpClientFindRecipeClient = Client(
        clientInfo = Implementation(
            name = "ig-mcp-client",
            version = "1.0.0"
        )
    )

    val mcpClientSaveRecipeClient = Client(
        clientInfo = Implementation(
            name = "storage-mcp-client",
            version = "1.0.0"
        )
    )

    try {
        mcpClientFindRecipeClient.connect(mcpTransportFindRecipe)
        println("✅ Подключено к серверу поиска рецептов")
    } catch (e: Exception) {
        println("❌ Ошибка подключения к серверу поиска рецептов: ${e.message}")
        mcpProcessFindRecipe.destroy()
        mcpProcessSaveRecipe.destroy()
        return@runBlocking
    }

    try {
        mcpClientSaveRecipeClient.connect(mcpTransportSaveRecipe)
        println("✅ Подключено к серверу сохранения рецептов")
    } catch (e: Exception) {
        println("❌ Ошибка подключения к серверу сохранения рецептов: ${e.message}")
        mcpProcessFindRecipe.destroy()
        mcpProcessSaveRecipe.destroy()
        return@runBlocking
    }

    val mcpServiceFindRecipe = McpService(mcpClientFindRecipeClient)
    val mcpServiceSaveRecipe = McpService(mcpClientSaveRecipeClient)

    println("✅ Все сервисы готовы!\n")

    // 🔥 ЗАПУСК ИНТЕРАКТИВНОГО РЕЖИМА
    try {
        startInteractiveMode(mcpServiceFindRecipe, mcpServiceSaveRecipe)
    } finally {
        println("🧹 Завершение работы серверов...")
        mcpClientFindRecipeClient.close()
        mcpClientSaveRecipeClient.close()
        mcpProcessFindRecipe.destroy()
        mcpProcessSaveRecipe.destroy()
        println("✅ Серверы остановлены")
    }
}

/**
 * Интерактивный режим работы с пользователем
 */
suspend fun startInteractiveMode(
    mcpServiceFindRecipe: McpService,
    mcpServiceSaveRecipe: McpService,
) {
    println("🤖 Агент запущен! Доступные команды:")
    println("   1 - Получить случайный рецепт")
    println("   2 - Сохранить рецепт (ручной ввод)")
    println("   3 - Получить и сохранить рецепт")
    println("   exit - Выход")
    println()

    while (true) {
        print("Введите команду: ")
        val input = readlnOrNull()?.trim() ?: continue

        when (input) {
            "1" -> {
                handleFindRecipe(mcpServiceFindRecipe)
            }
            "2" -> {
                handleSaveRecipe(mcpServiceSaveRecipe)
            }
            "3" -> {
                handleFindAndSaveRecipe(mcpServiceFindRecipe, mcpServiceSaveRecipe)
            }
            "exit" -> {
                println("👋 Завершение работы...")
                break
            }
            else -> {
                println("❌ Неизвестная команда. Попробуйте снова.")
            }
        }
        println()
    }
}

/**
 * Обработка команды "Получить рецепт"
 */
suspend fun handleFindRecipe(mcpServiceFindRecipe: McpService) {
    try {
        println("🔍 Получаю случайный рецепт...")

        val findRecipeResult = mcpServiceFindRecipe.callTool(
            toolName = "get_random_meal",
            arguments = emptyMap()
        )

        val recipeText = parseRecipe(findRecipeResult)

        println("✅ Рецепт получен:")
        println("   ID: ${recipeText.id}")
        println("   Название: ${recipeText.name}")
        println("   Регион: ${recipeText.area}")
        println("   Инструкция: ${recipeText.instruction.take(200)}${if (recipeText.instruction.length > 200) "..." else ""}")

    } catch (e: Exception) {
        println("❌ Ошибка при получении рецепта: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Обработка команды "Сохранить рецепт"
 */
suspend fun handleSaveRecipe(mcpServiceSaveRecipe: McpService) {
    try {
        print("Введите название рецепта: ")
        val title = readlnOrNull()?.trim()
        if (title.isNullOrBlank()) {
            println("❌ Название не может быть пустым")
            return
        }

        print("Введите содержание рецепта: ")
        val content = readlnOrNull()?.trim()
        if (content.isNullOrBlank()) {
            println("❌ Содержание не может быть пустым")
            return
        }

        println("💾 Сохраняю рецепт...")

        val saveRecipeResult = mcpServiceSaveRecipe.callTool(
            toolName = "save_recipe",
            arguments = mapOf(
                "title" to title,
                "content" to content
            )
        )

        println("✅ Рецепт сохранён!")
        println("   Результат: $saveRecipeResult")

    } catch (e: Exception) {
        println("❌ Ошибка при сохранении рецепта: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Обработка команды "Получить и сохранить рецепт"
 */
suspend fun handleFindAndSaveRecipe(
    mcpServiceFindRecipe: McpService,
    mcpServiceSaveRecipe: McpService
) {
    try {
        println("🔍 Получаю случайный рецепт...")

        // 1. Получаем рецепт
        val findRecipeResult = mcpServiceFindRecipe.callTool(
            toolName = "get_random_meal",
            arguments = emptyMap()
        )

        val recipeText = parseRecipe(findRecipeResult)

        println("✅ Рецепт получен:")
        println("   ID: ${recipeText.id}")
        println("   Название: ${recipeText.name}")
        println("   Регион: ${recipeText.area}")

        // 2. Сохраняем рецепт
        println("💾 Сохраняю рецепт...")

        val saveRecipeResult = mcpServiceSaveRecipe.callTool(
            toolName = "save_recipe",
            arguments = mapOf(
                "title" to recipeText.name,
                "content" to recipeText.instruction
            )
        )

        println("✅ Рецепт сохранён!")
        println("   Результат: $saveRecipeResult")

    } catch (e: Exception) {
        println("❌ Ошибка при получении и сохранении рецепта: ${e.message}")
        e.printStackTrace()
    }
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
                        You have access to multiple tools.

                        If the user asks to find a recipe:
                        - First, call the tool that retrieves the recipe.

                        If the user asks to save a recipe:
                        - Call the tool that saves content to a file.

                        If both are requested:
                        - Always retrieve the recipe first
                        - Then pass the retrieved content to the saving tool
                        - Do not skip steps
                        - Do not invent recipe content
                        
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

class McpService(
    private val client: Client
) {
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

@Serializable
data class ToolCall(
    val tool: String,
    val arguments: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class StoredRecipe(
    val id: String,
    val name: String,
    val area: String,
    val instruction: String,
    val timestamp: String
)

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
        instruction = dto.strInstructions ?: "неизвестный рецепт",
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