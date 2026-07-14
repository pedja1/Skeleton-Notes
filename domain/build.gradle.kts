import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    setSource(
        source.filter { file ->
            !file.path.contains("/test/") && !file.path.contains("/androidTest/")
        },
    )
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
