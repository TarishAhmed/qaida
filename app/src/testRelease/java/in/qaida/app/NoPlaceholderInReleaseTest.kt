package `in`.qaida.app

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Release-only guard: stub content is marked PLACEHOLDER, and none of it may reach a release.
 * Scans every asset and resource file that the release variant merges (src/release, src/main).
 */
class NoPlaceholderInReleaseTest {

    @Test
    fun `no PLACEHOLDER string in release assets or resources`() {
        val roots = System.getProperty("qaida.releaseRoots").orEmpty()
            .split(File.pathSeparator).filter { it.isNotEmpty() }.map(::File)
        assertTrue(roots.isNotEmpty(), "qaida.releaseRoots not set")

        val textExtensions = setOf("json", "xml", "txt", "svg")
        val offenders = roots
            .flatMap { listOf(File(it, "assets"), File(it, "res")) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension in textExtensions }.toList() }
            .filter { "PLACEHOLDER" in it.readText() }

        assertTrue(offenders.isEmpty(), "PLACEHOLDER found in release files:\n" + offenders.joinToString("\n"))
    }
}
