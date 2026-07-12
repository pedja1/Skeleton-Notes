import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Gradle task that checks whether assembled APK file sizes
 * exceed a configurable maximum size limit.
 */
abstract class CheckApkSizeTask : DefaultTask() {

    @get:Internal
    abstract val apkFile: RegularFileProperty

    @get:InputFiles
    abstract val apkFiles: ConfigurableFileCollection

    @get:Input
    abstract val maxSizeKb: Property<Long>

    /**
     * Verifies that the APK files exist and their sizes do not exceed [maxSizeKb].
     * Throws [GradleException] on failure.
     */
    @TaskAction
    fun check() {
        val files = if (apkFiles.isEmpty) {
            val f = apkFile.orNull?.asFile
            if (f != null) listOf(f) else emptyList()
        } else {
            apkFiles.files.toList()
        }

        if (files.isEmpty()) {
            throw GradleException("APK files not found. Run './gradlew assembleRelease' first.")
        }

        for (f in files) {
            if (!f.exists()) {
                throw GradleException("APK file not found: ${f.absolutePath}\nRun './gradlew assembleRelease' first.")
            }

            val sizeBytes = f.length()
            val maxBytes = maxSizeKb.get() * 1024

            if (sizeBytes > maxBytes) {
                val sizeKb = sizeBytes / 1024
                throw GradleException(
                    "APK size ($sizeKb KB) exceeds limit of ${maxSizeKb.get()} KB.\n" +
                        "File: ${f.absolutePath}"
                )
            }

            val sizeKb = sizeBytes / 1024
            logger.lifecycle(
                "${f.name}: $sizeKb KB (limit: ${maxSizeKb.get()} KB)"
            )
        }
    }
}
