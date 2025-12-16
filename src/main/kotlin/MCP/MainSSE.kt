//
//import io.ktor.client.*
//import io.ktor.client.call.*
//import io.ktor.client.engine.cio.*
//import io.ktor.client.plugins.sse.*
//import io.ktor.client.request.*
//import io.ktor.http.*
//import kotlinx.coroutines.runBlocking
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.Json
//
//@Serializable
//data class McpRequest(
//    @SerialName("jsonrpc") val jsonrpc: String = "2.0",
//    val id: Int = 1,
//    val method: String,
//    val params: Map<String, String> = emptyMap()
//)
//
//suspend fun main() = runBlocking {
//    val client = HttpClient(CIO) {
//        install(SSE)
//    }
//
//    // Сначала отправляем HTTP POST для инициализации (если требуется)
//    try {
//        println("Отправляю handshake запрос...")
//
//        val handshakeRequest = McpRequest(
//            method = "initialize",
//            params = mapOf("protocolVersion" to "2024-11-05")
//        )
//
//        val json = Json { prettyPrint = true }
//
//        // Некоторые MCP серверы требуют начальный HTTP запрос
//        val response: String = client.post("https://mcp.deepwiki.com/mcp") {
//            contentType(ContentType.parse("text/event-stream"))
//            contentType(ContentType.parse("application/json"))
//            accept(ContentType.Application.Json)
//            setBody(json.encodeToString(handshakeRequest))
//        }.body()
//
//        println("Handshake ответ: $response")
//
//    } catch (e: Exception) {
//        println("Handshake не требуется или ошибка: ${e.message}")
//    }
//
//    // Затем подключаемся к SSE потоку
//    println("\nПодключаюсь к SSE потоку...")
//
//    client.sse("https://mcp.deepwiki.com/mcp") {
//        val events = incoming.collect {
//            println(it)
//        }
//
////        var eventCount = 0
////        events.collect { serverSentEvent ->
////            when (serverSentEvent) {
////                is ServerSentEvent.Message -> {
////                    eventCount++
////                    println("Событие #$eventCount: ${serverSentEvent.data}")
////
////                    // Автоматически запрашиваем инструменты при подключении
////                    if (eventCount == 1) {
////                        // Можно отправить запрос на получение инструментов
////                        // через отдельное WebSocket соединение или другой канал
////                        requestTools()
////                    }
////                }
////                else -> { /* Обработка других типов событий */ }
////            }
////        }
//    }
//}
//
//suspend fun requestTools() {
//    // Для отправки команд на MCP сервер может потребоваться
//    // отдельное WebSocket соединение или HTTP запрос
//    println("Запрашиваю список инструментов...")
//
//    // Этот код зависит от конкретной реализации MCP сервера
//    // Обычно нужен WebSocket для двусторонней связи
//}