plugins {
    id("com.android.application")
}

android {
    namespace = "com.mmwtl.atlaslauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mmwtl.atlaslauncher"
        minSdk = 26
        targetSdk = 30
        versionCode = 4
        versionName = "0.4.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
