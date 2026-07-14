import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}
val versionProps =
    Properties().apply {
        rootProject.file("version.properties").inputStream().use { load(it) }
    }

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    setSource(
        source.filter { file ->
            !file.path.contains("/test/") && !file.path.contains("/androidTest/")
        },
    )
}

android {
    namespace = "org.skynetsoftware.skeletonnotes"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 0
            }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "org.skynetsoftware.skeletonnotes"
        minSdk = 24
        targetSdk = 37
        versionCode = (System.getenv("VERSION_CODE") ?: versionProps.getProperty("VERSION_CODE")).toInt()
        versionName = System.getenv("VERSION_NAME") ?: versionProps.getProperty("VERSION_NAME")

        testInstrumentationRunner = "org.skynetsoftware.skeletonnotes.SkeletonNotesTestRunner"
    }

    signingConfigs {
        val keystoreFile = System.getenv("KEYSTORE_FILE")
        if (keystoreFile != null) {
            create("ciRelease") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("ciRelease")?.let {
                signingConfig = it
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/**/LICENSE*",
                    "META-INF/**/NOTICE*",
                )
        }
    }
    testOptions {
        animationsDisabled = true
    }
}

dependencies {
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        // espresso-contrib pulls hamcrest 2.2, which conflicts with the hamcrest 1.3
        // used by the rest of the test suite (e.g. CoreMatchers.allOf(Matcher, Matcher)).
        exclude(group = "org.hamcrest")
    }
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.intents)
}
