package assistant

import java.io.File

data class SearchResult(
    val file: String,
    val snippet: String
)

object AssistantService {

    private val SOURCE_DIR = File("src/main/kotlin")

    fun ask(question: String): List<SearchResult> {
        if (!SOURCE_DIR.exists()) return emptyList()

        val keywords = question
            .lowercase()
            .split(" ", "_")
            .filter { it.length > 2 }

        val results = mutableListOf<SearchResult>()

        SOURCE_DIR.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val l = line.lowercase()
                    if (keywords.any { l.contains(it) }) {
                        val from = (index - 3).coerceAtLeast(0)
                        val to = (index + 3).coerceAtMost(lines.lastIndex)
                        val snippet = lines.subList(from, to + 1).joinToString("\n")
                        results.add(
                            SearchResult(
                                file = file.relativeTo(SOURCE_DIR).path,
                                snippet = snippet
                            )
                        )
                    }
                }
            }

        return results.take(5)
    }
}
