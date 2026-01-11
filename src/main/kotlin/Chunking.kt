import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

data class Chunk(
    val id: Int,
    val text: String,
    val embedding: FloatArray
)

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
//
//fun main() {
//    print("Введите вопрос: ")
//    val question = readLine()!!
//
////    // --- БЕЗ RAG ---
////    println("→ Вопрос без RAG")
////    val answerNoRag = askLLM(question)
//
//    // --- С RAG ---
//    println("→ Embedding вопроса")
//    val queryEmbedding = getEmbedding(question)
//
//    println("→ Поиск в FAISS")
//    val rawResults = searchFaiss(queryEmbedding)
//
////    val result = process.inputStream.bufferedReader().readText()
////    println("RAW FAISS OUTPUT:\n$result")
//
////    println("→ Вопрос с RAG")
////    val ragPrompt = buildRagPrompt(question, context)
//
//    val contextNoFilter = rawResults
//        .sortedByDescending { it.score }
//        .take(5)
//        .map { it.text }
//
//    println("→ Фильтрация / reranking")
//    val contextFiltered = filterRelevant(rawResults)
//
//    println("→ Вопрос с RAG (без фильтра)")
//    val answerRagNoFilter = askLLM(
//        buildRagPrompt(question, contextNoFilter)
//    )
//
//    println("→ Вопрос с RAG (с фильтром)")
//    val answerRagFiltered = askLLM(
//        buildRagPrompt(question, contextFiltered)
//    )
//
//    println("\n====== RAG БЕЗ ФИЛЬТРА ======\n")
//    println(answerRagNoFilter)
//
//    println("\n====== RAG С ФИЛЬТРОМ ======\n")
//    println(answerRagFiltered)
//}

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
        "./venv/bin/python",
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
    val memory = ChatMemory()

    println("RAG Chat. Для выхода введите 'exit'.")

    while (true) {
        print("\nВы: ")
        val question = readLine() ?: break
        if (question.lowercase() == "exit") break

        memory.add(Role.USER, question)

        println("→ Поиск контекста")
        val ragContext = retrieveContext(question)

        println("→ Генерация ответа")
        val prompt = buildChatRagPrompt(question, memory, ragContext)
        val answer = askLLM(prompt)

        memory.add(Role.ASSISTANT, answer)

        println("\nАссистент:\n$answer")

        printSources(ragContext)
    }
}


//какой срок действия товарного знака согласно п. 1 ст. 1491 ГК РФ?