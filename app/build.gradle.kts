import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

android {
    namespace = "com.test1.tv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.test1.tv"
        minSdk = 30
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
        // OMDB_API_KEY is set per build type below - no hardcoded fallback for security
    }

    buildTypes {
        debug {
            // Load test tokens from secrets.properties for integration tests
            val secretsFile = rootProject.file("secrets.properties")
            val secrets = Properties()
            if (secretsFile.exists()) {
                secrets.load(secretsFile.inputStream())
            }
            buildConfigField("String", "TRAKT_TEST_ACCESS_TOKEN",
                "\"${secrets.getProperty("TRAKT_TEST_ACCESS_TOKEN", "")}\"")
            buildConfigField("String", "TRAKT_TEST_REFRESH_TOKEN",
                "\"${secrets.getProperty("TRAKT_TEST_REFRESH_TOKEN", "")}\"")
            // Allow empty OMDB key in debug builds
            buildConfigField("String", "OMDB_API_KEY", "\"${secrets.getProperty("OMDB_API_KEY", "")}\"")
        }
        release {
            isMinifyEnabled = false     // Disabled temporarily until keep rules are validated
            isShrinkResources = false   // Keep resources intact while we analyze what shrinking needs
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
            // Empty test tokens for release builds
            buildConfigField("String", "TRAKT_TEST_ACCESS_TOKEN", "\"\"")
            buildConfigField("String", "TRAKT_TEST_REFRESH_TOKEN", "\"\"")
            // Require OMDB_API_KEY for release builds - fail if missing
            val secretsFile = rootProject.file("secrets.properties")
            val secrets = Properties()
            if (secretsFile.exists()) {
                secrets.load(secretsFile.inputStream())
            }
            val omdbKey = secrets.getProperty("OMDB_API_KEY", "")
            if (omdbKey.isEmpty()) {
                throw GradleException("OMDB_API_KEY not found in secrets.properties - required for release builds")
            }
            buildConfigField("String", "OMDB_API_KEY", "\"$omdbKey\"")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    lint {
        abortOnError = false
    }

    // Disable PNG crunching to prevent HWUI image decoder errors with certain compressed resources
    androidResources {
        noCompress += listOf("png", "webp")
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
    implementation(libs.androidx.palette)
    implementation(libs.glide)
    implementation(libs.glide.transformations)
    ksp(libs.glide.ksp)

    // Shimmer effect for skeleton loading UI
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // FlexboxLayout for flexible chip layouts
    implementation("com.google.android.flexbox:flexbox:3.0.0")

    // Hilt (Dependency Injection)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt Testing
    testImplementation(libs.hilt.testing)
    kspTest(libs.hilt.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.arch.core.testing)

    // Testing
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")

    // Lifecycle & ViewModel
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime)
    implementation(libs.androidx.fragment.ktx)

    // WorkManager
    implementation(libs.work.runtime)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Material Icons
    implementation(libs.material)

    // ExoPlayer (Media3)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media3:media3-extractor:1.2.1")
}
