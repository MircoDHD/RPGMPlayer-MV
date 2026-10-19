// SPDX-FileCopyrightText: 2026 MircoDHD
// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Bump both for every APK that is handed out or released (see CHANGELOG.md).
val appVersionCode = 11
val appVersionName = "0.4.0"

// Release signing from keystore.properties (not committed). Without it, release builds
// are left unsigned.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}

// APKs are named RPGM-Player-<version>-<debug|release>.apk instead of app-<type>.apk.
base {
    archivesName = "RPGM-Player-$appVersionName"
}

/** Puts CHANGELOG.md into the APK as assets/changelog/en.md for the in-app "What's new" box. */
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("changelog")
        dir.mkdirs()
        source.get().asFile.copyTo(dir.resolve("en.md"), overwrite = true)
    }
}

val copyChangelog = tasks.register<CopyChangelog>("copyChangelog") {
    source = rootProject.layout.projectDirectory.file("CHANGELOG.md")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyChangelog, CopyChangelog::outputDir)
    }
}

android {
    namespace = "io.github.mircodhd.mvplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mircodhd.rpgmplayermv"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
