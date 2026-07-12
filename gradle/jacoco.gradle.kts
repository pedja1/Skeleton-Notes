import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

allprojects {
    apply<JacocoPlugin>()
}

val fileFilter = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*\$ViewBinder*.*",
    "**/databinding/**",
    "**/di/**",
    "**/*_MembersInjector.*",
    "**/Dagger*.*",
    "**/*_Factory.*",
)

val coverageSourceDirs = files()
rootProject.subprojects.forEach { sub ->
    sub.afterEvaluate {
        coverageSourceDirs.from(projectDir.resolve("src/main"))
    }
}

val coverageClassDirs = files()

rootProject.subprojects.forEach { sub ->
    sub.afterEvaluate {
        val buildDir = layout.buildDirectory.get().asFile
        val hasAppPlugin = plugins.hasPlugin("com.android.application")
        val hasLibPlugin = plugins.hasPlugin("com.android.library")
        if (hasAppPlugin || hasLibPlugin) {
            val dirPathPattern = if (hasAppPlugin) {
                // Application module with flavors: both full and lite debug class dirs
                Regex("""intermediates/built_in_kotlinc/\w+Debug/compile\w+DebugKotlin/classes""")
            } else {
                // Library module
                "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
            }
            coverageClassDirs.from({
                val dir = java.io.File(buildDir, if (hasLibPlugin) dirPathPattern as String else "")
                if (hasAppPlugin) {
                    val pattern = dirPathPattern as Regex
                    val files = mutableListOf<java.io.File>()
                    buildDir.walkTopDown().forEach { candidate ->
                        val relative = candidate.relativeTo(buildDir).path
                        if (pattern.matches(relative) && candidate.isDirectory) {
                            files.add(candidate)
                        }
                    }
                    files.forEach { dir ->
                        fileTree(dir) { exclude(fileFilter) }
                    }
                    files
                } else {
                    if (dir.exists()) {
                        fileTree(dir) { exclude(fileFilter) }
                    } else {
                        files()
                    }
                }
            })
        } else {
            val dirPath = "classes/kotlin/main"
            coverageClassDirs.from({
                val dir = java.io.File(buildDir, dirPath)
                if (dir.exists()) {
                    fileTree(dir) { exclude(fileFilter) }
                } else {
                    files()
                }
            })
        }
    }
}

val coverageExecData = files()
rootProject.subprojects.forEach { sub ->
    sub.afterEvaluate {
        val buildDir = layout.buildDirectory.get().asFile
        coverageExecData.from({
            fileTree(buildDir) {
                include("outputs/unit_test_code_coverage/**/*.exec")
                include("outputs/code_coverage/**/*.ec")
                include("jacoco/*.exec")
            }
        })
    }
}

tasks.register<JacocoReport>("jacocoFullReport") {
    group = "verification"
    description = "Generates aggregated JaCoCo coverage report across all modules"

    dependsOn(
        rootProject.subprojects.flatMap { sub ->
            sub.tasks.matching { task ->
                task.name == "test" ||
                    task.name.contains("DebugUnitTest") ||
                    task.name.startsWith("compile")
            }
        }
    )

    jacocoClasspath = configurations["jacocoAnt"]

    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom(coverageSourceDirs)
    executionData.setFrom(coverageExecData)

    reports {
        xml.required.set(true)
        xml.outputLocation.set(rootProject.layout.buildDirectory.file("reports/jacoco/jacocoFullReport/jacocoFullReport.xml"))
        html.required.set(true)
        html.outputLocation.set(rootProject.layout.buildDirectory.dir("reports/jacoco/jacocoFullReport/html"))
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoFullCoverageVerification") {
    group = "verification"
    description = "Verifies aggregated JaCoCo coverage meets 80% threshold"

    dependsOn(tasks.named("jacocoFullReport"))

    jacocoClasspath = configurations["jacocoAnt"]

    classDirectories.setFrom(coverageClassDirs)
    executionData.setFrom(coverageExecData)

    violationRules {
        rule {
            limit {
                minimum = BigDecimal.valueOf(0.80)
            }
        }
    }
}
