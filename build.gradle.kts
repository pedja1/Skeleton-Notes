// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}

apply(from = "gradle/dependency-check.gradle.kts")

tasks.register("check") {
    group = "verification"
    description = "Runs all verification tasks"
    dependsOn("checkDependencyAllowlist")
}