val releaseApkDir = rootProject.layout.projectDirectory
    .dir("app/build/outputs/apk/release")

val releaseApkFile = releaseApkDir.file("app-release.apk")
val unsignedReleaseApkFile = releaseApkDir.file("app-release-unsigned.apk")

/**
 * Picks the APK to measure. When both the signed and unsigned variants exist (e.g. a leftover
 * signed APK from an earlier keystore build next to a freshly assembled unsigned one), the most
 * recently built file is used so the check never passes against a stale artifact.
 */
fun resolveApkFile(): java.io.File {
    val signed = releaseApkFile.asFile
    val unsigned = unsignedReleaseApkFile.asFile
    return when {
        signed.exists() && unsigned.exists() ->
            if (signed.lastModified() >= unsigned.lastModified()) signed else unsigned
        signed.exists() -> signed
        else -> unsigned
    }
}

tasks.register<CheckApkSizeTask>("checkApkSize") {
    group = "verification"
    description = "Verifies that the release APK is smaller than the configured size limit"
    dependsOn(":app:assembleRelease")
    // fileProvider defers the exists() probe to execution time; resolving eagerly at
    // configuration time would freeze the choice before assembleRelease has produced the APK.
    apkFile.fileProvider(providers.provider { resolveApkFile() })
    maxSizeKb.set(1024)
}
