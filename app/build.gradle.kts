import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.test1.tv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.test1.tv"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Load API keys from secrets.properties
        val secretsFile = rootProject.file("secrets.properties")
        val secrets = Properties()
        if (secretsFile.exists()) {
            secrets.load(secretsFile.inputStream())
        }

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${secrets.getProperty("TRAKT_CLIENT_ID", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${secrets.getProperty("TRAKT_CLIENT_SECRET", "")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${secrets.getProperty("TMDB_API_KEY", "")}\"")
        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"${secrets.getProperty("TMDB_ACCESS_TOKEN", "")}\"")
        buildConfigField("String", "OMDB_API_KEY", "\"${secrets.getProperty("OMDB_API_KEY", "a8787305")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.glide)
    kapt("com.github.bumptech.glide:compiler:4.16.0")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)

    // WorkManager
    implementation(libs.work.runtime)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Material Icons
    implementation(libs.material)
}
