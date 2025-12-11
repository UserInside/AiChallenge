import HttpClient.Companion.client
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

val history = mutableListOf<String>()

fun main() {


    runBlocking {
        println("Введите ваш запрос (или 'выход' для завершения):")
        var counter = 0
        while (true) {
            val userInput = readlnOrNull()?.trim() ?: ""

            history.add("user: $userInput")

            val llmAnswer = sendToLLM(history, userInput)

            history.add("assistant: $llmAnswer")

            println("-".repeat(50))
            println(llmAnswer)

            counter++

            if (counter == 5) {
                counter = 0
                compressDialog()

            }
            if (llmAnswer.startsWith("ЭВРИКА")) {
                break
            }
        }
    }

    client.close()

}


const val API_KEY =
    "MDE5YWRiMzgtYWZjOS03MzRlLTk0MzEtYmE2YTI1N2E3ZDZkOmI2ZGZiYjYzLTFlNjUtNGU4Zi1iMzZhLTBjNjY0NDcxZmJjMg=="

const val MODEL = "GigaChat-2"


suspend fun sendToLLM(
    history: List<String>,
    userInput: String,
): String {

    try {
        val accessTokenResponse = client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append(HttpHeaders.Accept, "application/json")
                append("RqUID", "4b77b5ba-e17c-4bad-9450-e01d0c77f157")
                append(HttpHeaders.Authorization, "Basic $API_KEY")
            }
            setBody("scope=GIGACHAT_API_PERS")
        }.bodyAsText()

        val accessToken = Json.decodeFromString<TokenAnswer>(accessTokenResponse).accessToken

        val response = client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(
                """
                    {
                    "model": "$MODEL",
                    "messages": [
                        {
                        "role": "system",
                        "content": "$systemPrompt"
                        },
                        {
                        "role": "user",
                        "content": "$userInput"
                        }
                    ],
                    "stream": false,
                    "max_tokens": 512,
                    "repetition_penalty": 1
                    }
                    """.trimIndent()
            )
        }.bodyAsText()

        val answerResponse = Json.decodeFromString<ChatCompletionResponse>(response)

//        val parsedTitle = Json.decodeFromString<ParsedJsonAnswer>(answer ?: "").title
//        val parsedDescription = Json.decodeFromString<ParsedJsonAnswer>(answer ?: "").description

        val answer = answerResponse.choices?.first()?.message?.content
        val promptTokens = answerResponse.usage?.promptTokens
        val completionTokens = answerResponse.usage?.completionTokens
        val totalTokens = answerResponse.usage?.totalTokens

//        return answer
        return "Ответ: $answer \npromptTokens: $promptTokens \ncompletionTokens: $completionTokens \ntotalTokens: $totalTokens "
//        return "Тайтл: $parsedTitle \nОписание: $parsedDescription"


    } catch (e: Exception) {
        return e.toString()
    }
}

suspend fun compressDialog() {
    try {
        val accessTokenResponse = client.post("https://ngw.devices.sberbank.ru:9443/api/v2/oauth") {
            headers {
                append(HttpHeaders.ContentType, "application/x-www-form-urlencoded")
                append(HttpHeaders.Accept, "application/json")
                append("RqUID", "4b77b5ba-e17c-4bad-9450-e01d0c77f157")
                append(HttpHeaders.Authorization, "Basic $API_KEY")
            }
            setBody("scope=GIGACHAT_API_PERS")
        }.bodyAsText()

        val accessToken = Json.decodeFromString<TokenAnswer>(accessTokenResponse).accessToken

        val answerResponse = client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
            headers {
                append(HttpHeaders.Accept, "application/json")
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
            setBody(
                """
                    {
                    "model": "$MODEL",
                    "messages": [
                        {
                        "role": "system",
                        "content": "Вы - эксперт по суммаризации диалогов на русском языке. Создавайте краткие, информативные суммаризации, которые сохраняют ключевой контекст для продолжения беседы."
                        },
                        {
                        "role": "user",
                        "content": "Требуется создать суммаризацию данного диалога: ${history}"
                        }
                    ],
                    "stream": false,
                    "max_tokens": 512,
                    "repetition_penalty": 1
                    }
                    """.trimIndent()
            )
        }.bodyAsText()

        val answer = Json.decodeFromString<ChatCompletionResponse>(answerResponse).choices?.first()?.message?.content
            ?: "ЛЛМ СЛОМАЛОСЬ"


        history.clear()
        history.add(answer)
//        return "Тайтл: $parsedTitle \nОписание: $parsedDescription"


    } catch (e: Exception) {

    }

}


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
    val usage: Usage? = null
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

@Serializable
data class ParsedJsonAnswer(
    val title: String,
    val description: String,
)

//Отвечай исключительно в формате JSON по следующей схеме: {\"title\":\"Название\",\"description\":\"Описание\"}

class HttpClient {

    companion object {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    }
                )
            }
            engine {
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                }
            }
        }
    }
}