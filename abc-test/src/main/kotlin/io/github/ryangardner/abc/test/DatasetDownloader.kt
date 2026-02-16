package io.github.ryangardner.abc.test

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.zip.ZipInputStream

public object DatasetDownloader {
    private const val BASE_URL: String = "https://zenodo.org/records/17694747/files/"
    private const val DATASET_DIR: String = "abc-dataset"

    @JvmStatic
    public fun main(args: Array<String>): Unit {
        if (args.isEmpty()) {
            downloadAndExtract(1)
            downloadAndExtract(2)
        } else {
            args.forEach { arg ->
                arg.toIntOrNull()?.let { downloadAndExtract(it) }
            }
        }
    }

    internal fun extract(zis: ZipInputStream, outputDir: File) {
        val canonicalOutputDir = outputDir.canonicalPath
        var entry = zis.nextEntry
        while (entry != null) {
            val newFile = File(outputDir, entry.name)
            val canonicalDestinationPath = newFile.canonicalPath

            if (!canonicalDestinationPath.startsWith(canonicalOutputDir + File.separator)) {
                throw IOException("Entry is outside of the target dir: ${entry.name}")
            }

            if (entry.isDirectory) {
                newFile.mkdirs()
            } else {
                newFile.parentFile.mkdirs()
                FileOutputStream(newFile).use { fos ->
                    zis.copyTo(fos)
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    public fun downloadAndExtract(batchNumber: Int): File {
        val batchName = "abc_notation_batch_%03d".format(batchNumber)
        val zipFileName = "$batchName.zip"
        val url = URL("$BASE_URL$zipFileName")
        val outputDir = File(DATASET_DIR, batchName)
        
        if (outputDir.exists() && outputDir.list()?.isNotEmpty() == true) {
            println("Dataset $batchName already exists. Skipping download.")
            return outputDir
        }

        outputDir.mkdirs()
        println("Downloading $url ...")
        
        val connection = url.openConnection()
        connection.connect()
        
        ZipInputStream(BufferedInputStream(connection.getInputStream())).use { zis ->
            extract(zis, outputDir)
        }
        println("Extracted to ${outputDir.absolutePath}")
        return outputDir
    }
}
