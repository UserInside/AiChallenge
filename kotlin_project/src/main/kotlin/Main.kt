
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class EmbedRequest(val text: String)

@Serializable
data class EmbedResponse(val vector: List<Double>)

fun main() = runBlocking {
    // Инициализация Ktor client с JSON
    val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
    }

    val queryText = "fun foo() = 42"

    // HTTP POST запрос к Python backend
    val response: EmbedResponse = client.post("http://localhost:8000/embed_query") {
        contentType(ContentType.Application.Json)
        setBody(EmbedRequest(text = queryText))
    }.body()

    println("Embedding vector length: ${response.vector.size}")
    println("First 10 values: ${response.vector.take(10)}")

    client.close()
}
