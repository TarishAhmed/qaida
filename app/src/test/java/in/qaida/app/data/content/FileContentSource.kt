package `in`.qaida.app.data.content

import java.io.File
import java.io.FileNotFoundException

/** Reads assets from source directories on disk; earlier roots override later ones, as AGP's asset merge does. */
class FileContentSource(private val roots: List<File>) : ContentSource {
    override fun readText(path: String): String =
        find(path)?.readText() ?: throw FileNotFoundException(path)

    override fun exists(path: String) = find(path) != null

    private fun find(path: String) = roots.map { File(it, path) }.firstOrNull { it.isFile }

    companion object {
        /** The asset roots of the variant under test, passed in by app/build.gradle.kts. */
        fun forVariantUnderTest() = FileContentSource(
            System.getProperty("qaida.assetRoots").orEmpty()
                .split(File.pathSeparator).filter { it.isNotEmpty() }.map(::File),
        )
    }
}
