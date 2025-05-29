plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.1.21"
    id("com.autonomousapps.dependency-analysis") version "2.17.0"
}

android {
    namespace = "com.example.websitereader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.websitereader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.media3.session)
    implementation(libs.google.material)
    implementation(libs.material.v1120)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.activity.compose)
    implementation(libs.ui)
    androidTestImplementation(libs.junit)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.foundation.layout)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui.text)
    implementation(libs.androidx.ui.unit)
    implementation(libs.androidx.core)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.media3.common)
    implementation(libs.datastore.preferences)
    implementation(libs.guava)
    implementation(libs.kotlinx.serialization.core)
    testImplementation(libs.junit)
    runtimeOnly(libs.kotlinx.coroutines.android)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.monitor)
    debugImplementation(libs.androidx.ui.tooling)
    debugRuntimeOnly(libs.androidx.ui.test.manifest)
    implementation(libs.androidx.appcompat.v140)
    implementation(libs.readability4j)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
}