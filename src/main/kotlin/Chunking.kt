import assistant.AssistantService
//import assistant.DevAssistant
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject
import java.util.Scanner


fun tokenize(text: String): List<String> {
    return text
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
}

fun chunkTokens(
    tokens: List<String>,
    chunkSize: Int,
    overlap: Int
): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0

    while (start < tokens.size) {
        val end = min(start + chunkSize, tokens.size)
        chunks.add(tokens.subList(start, end).joinToString(" "))
        if (end == tokens.size) break
        start += (chunkSize - overlap)
    }

    return chunks
}

fun getEmbedding(text: String): FloatArray {
    val url = URL("http://localhost:11434/api/embeddings")
    val conn = url.openConnection() as HttpURLConnection

    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true

    val payload = JSONObject()
        .put("model", "nomic-embed-text")
        .put("prompt", text)

    conn.outputStream.use {
        it.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
    }

    val response = conn.inputStream.bufferedReader().readText()
    val embeddingJson = JSONObject(response)
        .getJSONArray("embedding")

    val embedding = FloatArray(embeddingJson.length())
    for (i in 0 until embeddingJson.length()) {
        embedding[i] = embeddingJson.getDouble(i).toFloat()
    }

    return embedding
}
fun buildRagPrompt(question: String, context: List<String>): String {
    return """
        Ты отвечаешь на вопрос, используя ТОЛЬКО информацию из контекста.
        Если ответа в контексте нет — скажи "неизвестно".

        Контекст:
        ${context.joinToString("\n\n")}

        Вопрос:
        $question
    """.trimIndent()
}

fun searchFaiss(queryEmbedding: FloatArray): List<SearchResult> {
    val process = ProcessBuilder(
        "./venv/bin/python3",
        "faiss_search.py"
    ).start()

    val json = JSONArray(queryEmbedding.toList()).toString()

    process.outputStream.bufferedWriter().use {
        it.write(json)
    }

    val result = process.inputStream.bufferedReader().readText()
    val arr = JSONArray(result)

    return List(arr.length()) { i ->
        val obj = arr.getJSONObject(i)
        SearchResult(
            id = obj.getInt("id"),
            text = obj.getString("text"),
            score = obj.getDouble("score").toFloat(),
            source = obj.getString("source")
        )
    }
}

fun askLLM(prompt: String): String {
    val url = URL("http://localhost:11434/api/generate")
    val conn = url.openConnection() as HttpURLConnection

    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true

    conn.connectTimeout = 30_000
    conn.readTimeout = 0   // <<< ВАЖНО: БЕЗ ТАЙМАУТА

    val payload = JSONObject()
        .put("model", "llama3.1")
        .put("prompt", prompt)
        .put("stream", false)

    conn.outputStream.use {
        it.write(payload.toString().toByteArray())
    }

    val response = conn.inputStream.bufferedReader().readText()
    val json = JSONObject(response)

    return json.getString("response")
}

const val SIM_THRESHOLD = 0.75f
const val MAX_CONTEXT_CHUNKS = 5

fun filterRelevant(
    results: List<SearchResult>
): List<String> {

    return results
        .sortedBy { it.score }       // rerank
        .filter { it.score >= SIM_THRESHOLD }  // threshold
        .take(MAX_CONTEXT_CHUNKS)
        .map { it.text }
}


enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val role: Role,
    val content: String
)

data class SearchResult(
    val id: Int,
    val text: String,
    val score: Float,
    val source: String   // имя файла / документа
)

class ChatMemory(
    private val maxMessages: Int = 10
) {
    private val messages = mutableListOf<ChatMessage>()

    fun add(role: Role, content: String) {
        messages.add(ChatMessage(role, content))
        if (messages.size > maxMessages) {
            messages.removeAt(0)
        }
    }

    fun formatted(): String =
        messages.joinToString("\n") {
            when (it.role) {
                Role.USER -> "Пользователь: ${it.content}"
                Role.ASSISTANT -> "Ассистент: ${it.content}"
            }
        }
}

data class RagContext(
    val chunks: List<SearchResult>
)

fun retrieveContext(question: String): RagContext {
    val embedding = getEmbedding(question)
    val raw = searchFaiss(embedding)

    val filtered = raw
        .sortedBy { it.score }
        .filter { it.score >= SIM_THRESHOLD }
        .take(MAX_CONTEXT_CHUNKS)


    raw.forEach {
        println("score=${it.score} text=${it.text.take(50)}")
    }

    return RagContext(filtered)
}

fun buildChatRagPrompt(
    question: String,
    memory: ChatMemory,
    context: RagContext
): String {

    val contextText = context.chunks.joinToString("\n\n") { chunk ->
        "[Источник: ${chunk.source}]\n${chunk.text}"
    }

    return """
        Ты — ассистент. 
        Отвечай ТОЛЬКО на основе контекста.
        Если ответа нет — скажи "неизвестно".

        === История диалога ===
        ${memory.formatted()}

        === Контекст ===
        $contextText

        === Вопрос ===
        $question
    """.trimIndent()
}

fun printSources(context: RagContext) {
    println("\nИсточники:")
    context.chunks
        .distinctBy { it.source }
        .forEachIndexed { i, chunk ->
            println("[${i + 1}] ${chunk.source}")
        }
}


fun main() {
    println("=== Локальный /assistant/help режим ===")
    println("Введите '/help' или 'exit'")

    val scanner = Scanner(System.`in`)

    while (true) {
        print("\n> ")
        val cmd = scanner.nextLine().trim()

        when (cmd) {
            "exit" -> return
            "/help" -> {
                print("Введите вопрос: ")
                val question = scanner.nextLine()

                val results = AssistantService.ask(question)

                println("\n=== Ответ ассистента ===")
                if (results.isEmpty()) {
                    println("Ничего не найдено в коде проекта.")
                } else {
                    results.forEachIndexed { i, r ->
                        println("\n--- ${i + 1}. ${r.file} ---")
                        println(r.snippet)
                    }
                }
            }
            else -> println("Неизвестная команда")
        }
    }
}
