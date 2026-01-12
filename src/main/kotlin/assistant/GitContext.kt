package assistant

import java.io.File
import java.io.BufferedReader

//
//object GitHelper {
//
//    private fun runGitCommand(vararg args: String): String {
//        val proc = ProcessBuilder(*args)
//            .directory(File(".")) // корень репозитория
//            .redirectErrorStream(true)
//            .start()
//        return proc.inputStream.bufferedReader().use(BufferedReader::readText).trim()
//    }
//
//    fun getGitContext(): GitContext {
//        val branch = runGitCommand("git", "rev-parse", "--abbrev-ref", "HEAD")
//        val commit = runGitCommand("git", "rev-parse", "HEAD")
//
//        val status = runGitCommand("git", "status", "--porcelain")
//        val dirty = status.isNotEmpty()
//
//        // Список изменённых файлов, без .idea, venv, assistant
//        val changedFiles = status.lines().mapNotNull {
//            if (it.length > 3) it.substring(3).trim() else null
//        }.filter {
//            !it.startsWith(".idea/") &&
//                    !it.startsWith("venv/") &&
//                    !it.startsWith("assistant/") &&
//                    !it.startsWith("build/") &&
//                    !it.startsWith("index.faiss")
//        }
//
//        return GitContext(branch, commit, dirty, changedFiles)
//    }
//
//}
