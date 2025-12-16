import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

fun main() = runBlocking {
    // 1. Создаём настроенный HTTP-клиент с SSE плагином
    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.sse.SSE)  // Устанавливаем SSE плагин
         install(ContentNegotiation) {
             json(
                 Json {
                     isLenient = true
                     prettyPrint = true
                     ignoreUnknownKeys = true
                 }
             )
         }
    }

    // 2. Создаём MCP клиент
    val client = Client(
        clientInfo = Implementation(
            name = "my-mcp-client",
            version = "1.0.0"
        )
    )

    // 3. Создаём транспорт с настроенным HTTP-клиентом
    val transport = StreamableHttpClientTransport(
        client = httpClient,  // Передаём настроенный клиент
        url = "https://remote.mcpservers.org/fetch/mcp",
    )

    try {
        println("🔗 Подключаюсь к MCP серверу...")
        client.connect(transport)

        println("📋 Получаю список инструментов...")
        val tools = client.listTools()

        println("\n✅ Успешно! Доступно инструментов: ${tools.tools.size}")
        tools.tools.forEachIndexed { i, tool ->
            println("\n${i + 1}. ${tool.name}")
            println("   Описание: ${tool.description}")
        }

    } catch (e: Exception) {
        println("❌ Ошибка: ${e.javaClass.simpleName} - ${e.message}")
    } finally {
        // Закрываем оба клиента
        httpClient.close()
        client.close()
    }
}
