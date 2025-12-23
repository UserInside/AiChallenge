import java.io.File
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

fun main() {
    val inputText = File("/home/igor/Desktop/4pGK.txt").readText()
    val tokens = tokenize(inputText)

    val chunks = chunkTokens(
        tokens = tokens,
        chunkSize = 100,
        overlap = 64,
    )

    val result = JSONArray()

    chunks.forEachIndexed { index, chunkText ->
        println("Embedding chunk $index")
        val embedding = getEmbedding(chunkText)

        val obj = JSONObject()
        obj.put("id", index)
        obj.put("text", chunkText)
        obj.put("embedding", JSONArray(embedding.toList()))

        result.put(obj)
    }

    File("embeddings.json").writeText(result.toString())
    println("Saved embeddings.json")
}