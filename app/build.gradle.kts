plugins {
    id("com.android.application")
}

android {
    namespace = "au.com.josephmark.dithertracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "au.com.josephmark.dithertracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
}
