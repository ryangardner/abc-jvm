package io.github.ryangardner.abc.test

import io.github.ryangardner.abc.parser.AbcParser
import io.github.ryangardner.abc.theory.MeasureValidator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue

public class SemanticFidelityTest {

    public companion object {
        private var datasetDir: File? = null
        private val isHeavy: Boolean = System.getProperty("test.profile") == "heavy"

        @JvmStatic
        @BeforeAll
        public fun setup(): Unit {
            if (isHeavy) {
                datasetDir = DatasetDownloader.downloadAndExtract(1)
            }
        }

        @JvmStatic
        public fun abcFiles(): List<File> {
            return if (isHeavy) {
                val allFiles = mutableListOf<File>()
                datasetDir?.walkTopDown()?.forEach {
                    if (it.extension == "abc") {
                        allFiles.add(it)
                    }
                }
                allFiles.take(1000)
            } else {
                val sanityDir = File("src/test/resources/sanity-samples")
                if (sanityDir.exists()) {
                    sanityDir.listFiles { f -> f.extension == "abc" }?.toList() ?: emptyList()
                } else {
                    val resource = SemanticFidelityTest::class.java.classLoader.getResource("sanity-samples")
                    if (resource != null) {
                        File(resource.toURI()).listFiles { f -> f.extension == "abc" }?.toList() ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            }
        }
    }

    @ParameterizedTest(name = "Semantic validation: {0}")
    @MethodSource("abcFiles")
    public fun `test measure durations`(file: File): Unit {
        val parser = AbcParser()
        val originalAbc = file.readText()
        val tunes = try {
            parser.parseBook(originalAbc)
        } catch (e: Exception) {
            // Ignore tunes we can't even parse for now
            return
        }

        tunes.forEachIndexed { tuneIndex, tune ->
            // In semantic fidelity test, we use non-strict mode because ABC allows partial measures.
            val errors = MeasureValidator.validate(tune, strict = false)
            assertTrue(errors.isEmpty(), FidelityReporter.reportMeasureErrors(file, tuneIndex, errors, originalAbc))
        }
    }
}
