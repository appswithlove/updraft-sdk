import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.updraft)
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
    implementation("androidx.compose.foundation:foundation:${libs.versions.composeUi.get()}")
}

val updraftUploadUrl: String = findProperty("updraft_uploadUrl") as? String ?: ""
updraft {
    urls = mapOf("Release" to listOf(updraftUploadUrl))
}
