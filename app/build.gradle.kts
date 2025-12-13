import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-parcelize")
}

// Version configuration - can be overridden by CI with -PVERSION_NAME=x.x.x
val appVersionName: String = project.findProperty("VERSION_NAME")?.toString() ?: "1.0.0-alpha"
val appVersionCode: Int = generateVersionCode(appVersionName)

fun generateVersionCode(versionName: String): Int {
    // Parse version: "1.2.3-beta" -> major=1, minor=2, patch=3, prerelease=1
    val regex = """(\d+)\.(\d+)\.(\d+)(?:-(\w+))?""".toRegex()
    val match = regex.matchEntire(versionName)
        ?: throw GradleException("Invalid version format: $versionName. Expected: MAJOR.MINOR.PATCH[-PRERELEASE]")

    val (major, minor, patch, prerelease) = match.destructured

    // Pre-release suffix: alpha=0, beta=1, (none)=2
    val prereleaseCode = when (prerelease.lowercase()) {
        "alpha" -> 0
        "beta" -> 1
        "" -> 2
        else -> 2
    }

    // Format: MMNNPPPR (Major, Minor, Patch, Prerelease)
    // Example: 1.2.3-beta = 1 * 1000000 + 2 * 10000 + 3 * 10 + 1 = 1020031
    return major.toInt() * 1_000_000 +
           minor.toInt() * 10_000 +
           patch.toInt() * 10 +
           prereleaseCode
}

android {
    namespace = "com.strmr.tv"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.strmr.tv"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        // Load API keys from secrets.properties OR environment variables (CI)
        val secretsFile = rootProject.file("secrets.properties")
        val secrets = Properties()
        if (secretsFile.exists()) {
            secrets.load(secretsFile.inputStream())
        }

        // Helper function: prefer environment variable, fallback to secrets.properties
        fun getSecret(key: String, allowEmpty: Boolean = false): String {
            val value = System.getenv(key) ?: secrets.getProperty(key, "")
            if (value.isEmpty() && !allowEmpty) {
                logger.warn("Warning: $key not found in environment or secrets.properties")
            }
            return value
        }

        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${getSecret("TRAKT_CLIENT_ID")}\"")
        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${getSecret("TRAKT_CLIENT_SECRET")}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${getSecret("TMDB_API_KEY")}\"")
        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"${getSecret("TMDB_ACCESS_TOKEN")}\"")
        // OpenSubtitles API key for external subtitles
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${getSecret("OPENSUBTITLES_API_KEY", allowEmpty = true)}\"")
        // Premiumize OAuth credentials
        buildConfigField("String", "PREMIUMIZE_CLIENT_ID", "\"${getSecret("PREMIUMIZE_CLIENT_ID")}\"")
        buildConfigField("String", "PREMIUMIZE_CLIENT_SECRET", "\"${getSecret("PREMIUMIZE_CLIENT_SECRET")}\"")

        // Update checker configuration
        buildConfigField("String", "GITHUB_REPO", "\"PenceP/strmr\"")
    }

    signingConfigs {
        create("release") {
            // For CI: keystore is decoded from base64 and placed at this path
            // For local: you can configure your own keystore path
            val keystorePath = System.getenv("KEYSTORE_PATH") ?: "keystore/release.jks"
            val keystoreFile = file(keystorePath)

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
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
            buildConfigField("String", "OMDB_API_KEY",
                "\"${System.getenv("OMDB_API_KEY") ?: secrets.getProperty("OMDB_API_KEY", "")}\"")

            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Use release signing config if available, fallback to debug for local builds
            val releaseSigningConfig = signingConfigs.findByName("release")
            signingConfig = if (releaseSigningConfig?.storeFile?.exists() == true) {
                releaseSigningConfig
            } else {
                signingConfigs.getByName("debug")
            }

            // Empty test tokens for release builds
            buildConfigField("String", "TRAKT_TEST_ACCESS_TOKEN", "\"\"")
            buildConfigField("String", "TRAKT_TEST_REFRESH_TOKEN", "\"\"")

            // OMDB_API_KEY - prefer environment variable, fallback to secrets.properties
            val secretsFile = rootProject.file("secrets.properties")
            val secrets = Properties()
            if (secretsFile.exists()) {
                secrets.load(secretsFile.inputStream())
            }
            val omdbKey = System.getenv("OMDB_API_KEY") ?: secrets.getProperty("OMDB_API_KEY", "")
            if (omdbKey.isEmpty()) {
                logger.warn("Warning: OMDB_API_KEY not found - some features may not work")
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
