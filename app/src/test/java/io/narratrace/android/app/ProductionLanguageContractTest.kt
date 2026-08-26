package io.narratrace.android.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class ProductionLanguageContractTest {
    @Test
    fun `customer visible android surfaces do not use beta positioning`() {
        val customerSurfaceFiles = sequenceOf(
            File("src/main/java"),
            File("src/main/res"),
        ).flatMap { root ->
            root.walkTopDown().filter { file ->
                file.isFile && file.extension in setOf("kt", "xml")
            }
        }

        val betaReference = Regex("\\bbeta\\b", RegexOption.IGNORE_CASE)
        val offendingFiles = customerSurfaceFiles
            .filter { file -> betaReference.containsMatchIn(file.readText()) }
            .map { file -> file.relativeTo(File(".")).path }
            .toList()

        assertFalse(
            "Customer-visible Android Kotlin/XML must use production-service language; found beta positioning in: $offendingFiles",
            offendingFiles.isNotEmpty(),
        )
    }
}
