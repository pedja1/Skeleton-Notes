import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

abstract class CheckApkSizeTask : DefaultTask() {

    @get:Internal
    abstract val apkFile: RegularFileProperty

    @get:Input
    abstract val maxSizeKb: Property<Long>

    @TaskAction
    fun check() {
        val file = apkFile.get().asFile

        if (!file.exists()) {
            throw GradleException("APK file not found: ${file.absolutePath}\nRun './gradlew :app:assembleRelease' first.")
        }

        val sizeBytes = file.length()
        val maxBytes = maxSizeKb.get() * 1024

        if (sizeBytes > maxBytes) {
            val sizeKb = sizeBytes / 1024
            throw GradleException(
                "APK size ($sizeKb KB) exceeds limit of ${maxSizeKb.get()} KB.\n" +
                    "File: ${file.absolutePath}"
            )
        }

        val sizeKb = sizeBytes / 1024
        logger.lifecycle(
            "APK size check passed: $sizeKb KB (limit: ${maxSizeKb.get()} KB)"
        )
    }
}
