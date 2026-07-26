import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.loco)
    alias(libs.plugins.maven.publish)
}

Loco {
    config {
        lang = listOf("en", "de")
        defLang = "en"
        resDir = "$projectDir/src/commonMain/composeResources"
        fallbackLang = "en"
        orderByAssetId = true
        hideComments = true
    }
}

kotlin {
    androidLibrary {
        namespace = "com.appswithlove.updraft.ui"
        compileSdk = 36
        minSdk = 23
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
        androidResources {
            enable = true
        }
        withHostTestBuilder {}
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "UpdraftUI"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":updraft-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "com.appswithlove.updraft.ui.resources"
    publicResClass = false
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
