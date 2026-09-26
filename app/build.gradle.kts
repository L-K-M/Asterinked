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
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.lifecycle)
    implementation(libs.livedata)
    implementation(libs.pdfbox)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
