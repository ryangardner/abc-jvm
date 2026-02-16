package io.github.ryangardner.abc.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith

class DatasetDownloaderSecurityTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `test zip slip vulnerability`() {
        val outputDir = File(tempDir, "output")
        outputDir.mkdirs()

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            val entry = ZipEntry("../evil.txt")
            zos.putNextEntry(entry)
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
        }

        val zis = ZipInputStream(ByteArrayInputStream(bos.toByteArray()))

        // Before the fix, this should NOT throw an IOException (it should create the file outside outputDir)
        // After the fix, this SHOULD throw an IOException
        assertFailsWith<IOException> {
            DatasetDownloader.extract(zis, outputDir)
        }
    }
}
