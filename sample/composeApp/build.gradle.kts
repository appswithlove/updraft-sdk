import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val generateSampleKeys = tasks.register("generateSampleKeys") {
    val appKeyAndroid = localProperties.getProperty("updraft.appKey.android") ?: ""
    val appKeyIos = localProperties.getProperty("updraft.appKey.ios") ?: ""
    val sdkKey = localProperties.getProperty("updraft.sdkKey") ?: ""
    val outputDir = layout.buildDirectory.dir("generated/sampleKeys/kotlin")
    inputs.property("appKeyAndroid", appKeyAndroid)
    inputs.property("appKeyIos", appKeyIos)
    inputs.property("sdkKey", sdkKey)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("com/appswithlove/updraftsdk")
        packageDir.mkdirs()
        packageDir.resolve("SampleKeys.kt").writeText(
            """
            package com.appswithlove.updraftsdk

            object SampleKeys {
                const val APP_KEY_ANDROID = "$appKeyAndroid"
                const val APP_KEY_IOS = "$appKeyIos"
                const val SDK_KEY = "$sdkKey"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    androidLibrary {
        namespace = "com.appswithlove.updraftsdk.shared"
        compileSdk = 36
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SampleApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSampleKeys)
        }
        commonMain.dependencies {
            implementation(project(":updraft-core"))
            implementation(project(":updraft-ui-compose"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
    }
}
