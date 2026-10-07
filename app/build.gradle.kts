plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ch.lkmc.asterinked"
    compileSdkVersion("android-37.0")

    defaultConfig {
        applicationId = "ch.lkmc.asterinked"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }

    signingConfigs {
        // A checked-in debug keystore signs BOTH build types: zero-secret CI,
        // reproducible builds, anyone can build an upgrade-compatible APK.
        // Deliberate sideload-only decision — see docs/decisions/0001.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// PdfEngineRasterTest writes the fixtures that scripts/verify_pdf.py re-renders.
// Declaring them as an output makes a build-cache hit restore them instead of
// skipping the test run and leaving the PDFium check without input.
tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    outputs.dir(layout.buildDirectory.dir("test-output/raster-proof"))
}

dependencies {
    implementation(libs.activity)
    implementation(libs.lifecycle)
    implementation(libs.livedata)
    implementation(libs.pdfbox)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
