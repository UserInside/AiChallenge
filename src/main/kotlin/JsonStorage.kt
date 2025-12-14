

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*

object ConversationStorage {
    private const val STORAGE_DIR = "./conversations"
    private val json = Json {
        prettyPrint = true
    }

    init {
        // Создаем директорию если ее нет
        File(STORAGE_DIR).mkdirs()
    }

    /**
     * Сохраняет переписку в JSON файл
     * @param conversation - переписка для сохранения
     * @param filename - имя файла (если null, используется ID диалога)
     */
    fun saveConversation(conversation: Conversation, filename: String? = null): String {
        val file = File(STORAGE_DIR, filename ?: "${conversation.id}.json")

        try {
            val jsonString = json.encodeToString(conversation)
            file.writeText(jsonString, Charsets.UTF_8)
            println("💾 Сохранено в: ${file.path}")
            return file.path
        } catch (e: Exception) {
            println("❌ Ошибка сохранения: ${e.message}")
            throw e
        }
    }

    /**
     * Загружает переписку из JSON файла
     * @param conversationId - ID диалога (ищет файл с именем {conversationId}.json)
     */
    fun loadConversation(conversationId: String): Conversation? {
        val file = File(STORAGE_DIR, "$conversationId.json")

        return try {
            if (!file.exists()) {
                println("⚠️ Файл не найден: ${file.path}")
                return null
            }

            val jsonString = file.readText(Charsets.UTF_8)
            val conversation = json.decodeFromString<Conversation>(jsonString)
            println("📂 Загружен диалог: $conversationId (сообщений: ${conversation.messages.size})")
            conversation
        } catch (e: Exception) {
            println("❌ Ошибка загрузки: ${e.message}")
            null
        }
    }

    /**
     * Загружает последний диалог из папки
     */
    fun loadLastConversation(): Conversation? {
        val conversationsDir = File(STORAGE_DIR)
        val jsonFiles = conversationsDir.listFiles { file ->
            file.extension == "json"
        } ?: return null

        if (jsonFiles.isEmpty()) return null

        // Берем самый свежий файл
        val latestFile = jsonFiles.maxByOrNull { it.lastModified() }
        return latestFile?.let { file ->
            val jsonString = file.readText(Charsets.UTF_8)
            json.decodeFromString<Conversation>(jsonString)
        }
    }

    /**
     * Возвращает список всех сохраненных диалогов
     */
    fun listConversations(): List<String> {
        val conversationsDir = File(STORAGE_DIR)
        return conversationsDir.listFiles { file ->
            file.extension == "json"
        }?.map { it.nameWithoutExtension } ?: emptyList()
    }

    /**
     * Удаляет диалог из хранилища
     */
    fun deleteConversation(conversationId: String): Boolean {
        val file = File(STORAGE_DIR, "$conversationId.json")
        return if (file.exists()) {
            val deleted = file.delete()
            if (deleted) {
                println("🗑️ Удален диалог: $conversationId")
            }
            deleted
        } else {
            false
        }
    }
}

// Расширения для удобства
fun Conversation.saveToFile(filename: String? = null): String {
    return ConversationStorage.saveConversation(this, filename)
}

fun String.loadConversation(): Conversation? {
    return ConversationStorage.loadConversation(this)
}

@Serializable
data class Conversation (
    val id: String = "conv_${UUID.randomUUID()}",
    val messages: MutableList<Message> = mutableListOf(),
)

@Serializable
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
) {
    @Serializable
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM,
        SUMMARY
    }
}
