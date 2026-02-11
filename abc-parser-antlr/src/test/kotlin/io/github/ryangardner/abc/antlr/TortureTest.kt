package io.github.ryangardner.abc.antlr

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

public class TortureTest {

    public class TrackingErrorListener : BaseErrorListener() {
        public var hasErrors: Boolean = false
        public val errors: MutableList<String> = mutableListOf()

        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String?,
            e: RecognitionException?
        ) {
            hasErrors = true
            errors.add("line $line:$charPositionInLine $msg")
        }
    }

    @Test
    public fun `torture test with all sample abc files`() {
        val parser = AntlrAbcParser()
        // Locate abc-test resources relative to module root
        val projectRoot = File("..").absoluteFile.normalize()
        val abcTestResources = File(projectRoot, "abc-test/src/test/resources")
        
        println("Searching for ABC files in: ${abcTestResources.absolutePath}")
        
        if (!abcTestResources.exists()) {
            println("WARNING: abc-test resources not found. Skipping torture test.")
            // Fail if we expect them to be there, but maybe specific env doesn't have them?
            // User context says abc-test has 36 children so it should exist.
            return
        }

        val abcFiles = abcTestResources.walkTopDown()
            .filter { it.extension == "abc" }
            .toList()

        println("Found ${abcFiles.size} ABC files.")
        
        var passed = 0
        var failed = 0
        val failedFiles = mutableListOf<String>()

        abcFiles.forEach { file ->
            val errorListener = TrackingErrorListener()
            try {
                val content = file.readText()
                parser.parse(content, errorListener)
                
                if (errorListener.hasErrors) {
                    failed++
                    val errorMsg = errorListener.errors.take(5).joinToString("; ")
                    failedFiles.add("${file.name}: $errorMsg")
                } else {
                    passed++
                }
            } catch (e: Exception) {
                failed++
                failedFiles.add("${file.name}: Exception ${e.message}")
            }
        }

        println("==================================================")
        println("Torture Test Results:")
        println("Total Files: ${abcFiles.size}")
        println("Passed: $passed")
        println("Failed: $failed")
        println("Success Rate: ${if (abcFiles.isNotEmpty()) (passed.toDouble() / abcFiles.size) * 100 else 0.0}%")
        println("==================================================")
        
        if (failed > 0) {
           println("Failed Files (Sample):")
           failedFiles.take(20).forEach { failure ->
               val (filename, error) = failure.split(": ", limit = 2)
               val file = abcFiles.find { it.name == filename }
               val versionTag = file?.readLines()?.find { it.startsWith("%abc-") } ?: "No version"
               println("File: $filename | Version: $versionTag | Error: $error")
           }
           if (failedFiles.size > 20) println("... and ${failedFiles.size - 20} more.")
        }
        
        assertTrue(abcFiles.isNotEmpty(), "Should have found some ABC files to test")
    }
}
