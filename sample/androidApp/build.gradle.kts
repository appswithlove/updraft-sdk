import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // TODO(#16): com.appswithlove.updraft 2.3.0 is incompatible with AGP 9 —
    // casts ApplicationExtension to the removed legacy AppExtension
    // ("ApplicationExtensionImpl$AgpDecorated_Decorated cannot be cast to AppExtension").
    // Re-enable once the plugin ships AGP 9 support.
    // alias(libs.plugins.updraft)
}

android {
    namespace = "com.appswithlove.updraftsdk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.appswithlove.updraftsdk"
        minSdk = 23
        targetSdk = 36
        versionCode = 6
        versionName = "1.5"
    }

    signingConfigs {
        create("release") {
            storeFile = file("updraft_test.jks")
            storePassword = "appswithlove"
            keyAlias = "release"
            keyPassword = "appswithlove"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":sample:composeApp"))
    implementation(project(":updraft-sdk"))
    implementation(libs.androidx.activity.compose)
}

// val updraftUploadUrl: String = findProperty("updraft_uploadUrl") as? String ?: ""
// updraft {
//     urls = mapOf("Release" to listOf(updraftUploadUrl))
// }
