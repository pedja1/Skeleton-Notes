val flavorDirs = listOf("full", "lite")

fun resolveApkFiles(): List<java.io.File> {
    return flavorDirs.flatMap { flavor ->
        val dir = rootProject.layout.projectDirectory
            .dir("app/build/outputs/apk/$flavor/release")
        val signed = dir.file("app-$flavor-release.apk").asFile
        val unsigned = dir.file("app-$flavor-release-unsigned.apk").asFile
        if (signed.exists()) listOf(signed) else if (unsigned.exists()) listOf(unsigned) else emptyList()
    }
}

tasks.register<CheckApkSizeTask>("checkApkSize") {
    group = "verification"
    description = "Verifies that all flavor release APKs are smaller than the configured size limit"
    apkFiles.from(*resolveApkFiles().toTypedArray())
    maxSizeKb.set(1024)
}
