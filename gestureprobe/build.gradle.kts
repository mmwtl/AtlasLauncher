plugins {
    id("com.android.application")
}

android {
    namespace = "com.mmwtl.gestureprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mmwtl.gestureprobe"
        minSdk = 26
        targetSdk = 30
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
