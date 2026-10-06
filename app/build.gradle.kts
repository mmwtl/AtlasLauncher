plugins {
    id("com.android.application")
}

val appVersionCode = 19
val appVersionName = "1.2.0"
val branchName = providers.exec {
    commandLine("git", "branch", "--show-current")
}.standardOutput.asText.get().trim()
val safeBranchName = branchName.replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('.', '-').ifEmpty { "detached" }
val effectiveVersionName = if (branchName == "main") appVersionName else "$appVersionName-$safeBranchName"
val secureSigningFile = providers.gradleProperty("secure.signing").orNull?.let { rootProject.file(it) }

base {
    archivesName.set("$effectiveVersionName[${appVersionCode}]AtlasLauncher")
}

android {
    namespace = "com.mmwtl.atlaslauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geely.atlaslauncher"
        minSdk = 26
        targetSdk = 30
        versionCode = appVersionCode
        versionName = effectiveVersionName
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

if (secureSigningFile != null && secureSigningFile.isFile) {
    apply(from = secureSigningFile)
}
