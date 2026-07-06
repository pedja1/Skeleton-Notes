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
        val dirPath = if (plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
            "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
        } else {
            "classes/kotlin/main"
        }
        val buildDir = layout.buildDirectory.get().asFile
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
                    task.name.startsWith("testDebug") ||
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
