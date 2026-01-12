package assistant

import java.io.File
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.*

//data class Chunk(val id: String, val text: String, val embedding: List<Float>)
//
//class DevAssistant {
//
//    private val STORAGE_DIR = "./assistant_storage"
//    private val mapper = jacksonObjectMapper()
//    private val chunks: List<Chunk>
//
//    init {
//        File(STORAGE_DIR).mkdirs()
//        // Загружаем локальные эмбеддинги
//        chunks = loadChunks()
//    }
//
//    private fun loadChunks(): List<Chunk> {
//        val file = File("$STORAGE_DIR/assistant_embeddings.json")
//        if (!file.exists()) return emptyList()
//        return mapper.readValue(file)
//    }
//
//    fun ask(question: String): String {
//        // 1. Получаем top-K chunk'ов через Python Faiss search
//        val topChunks = searchFaissPython(question)
//
//        // 2. Формируем prompt для LLM
//        val contextText = topChunks.joinToString("\n\n") { it.text }
//        val prompt = """
//            Ты — ассистент. Отвечай ТОЛЬКО на основе контекста.
//            Если ответа нет — скажи "неизвестно".
//
//            Контекст:
//            $contextText
//
//            Вопрос:
//            $question
//        """.trimIndent()
//
//        // 3. Отправляем prompt в Python LLM и получаем ответ
//        return queryPythonLLM(prompt)
//    }
//
//    private fun searchFaissPython(question: String, topK: Int = 5): List<Chunk> {
//        // вызываем Python скрипт, который возвращает JSON с topK chunk'ами
//        val process = ProcessBuilder("python3", "assistant/search_faiss.py", question, topK.toString())
//            .directory(File("."))
//            .start()
//        val result = process.inputStream.bufferedReader().readText()
//        process.waitFor()
//        return mapper.readValue(result)
//    }
//
//    private fun queryPythonLLM(prompt: String): String {
//        val process = ProcessBuilder("python3", "assistant/llm_query.py")
//            .directory(File("."))
//            .start()
//
//        val writer = process.outputStream.bufferedWriter()
//        writer.write(prompt)
//        writer.flush()
//        writer.close()
//
//        val answer = process.inputStream.bufferedReader().readText()
//        process.waitFor()
//        return answer
//    }
//}
