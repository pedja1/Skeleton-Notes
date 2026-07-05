val releaseApkDir = rootProject.layout.projectDirectory
    .dir("app/build/outputs/apk/release")

val releaseApkFile = releaseApkDir.file("app-release.apk")
val unsignedReleaseApkFile = releaseApkDir.file("app-release-unsigned.apk")

fun resolveApkFile(): java.io.File {
    val signed = releaseApkFile.asFile
    if (signed.exists()) return signed
    return unsignedReleaseApkFile.asFile
}

tasks.register<CheckApkSizeTask>("checkApkSize") {
    group = "verification"
    description = "Verifies that the release APK is smaller than the configured size limit"
    apkFile.set(resolveApkFile())
    maxSizeKb.set(500)
}
