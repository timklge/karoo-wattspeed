import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "de.timklge.karoowattspeed"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.timklge.karootouchpad"
        minSdk = 28
        targetSdk = 34
        versionCode = 100 + (System.getenv("BUILD_NUMBER")?.toInt() ?: 1)
        versionName = System.getenv("RELEASE_VERSION") ?: "1.0"
    }

    signingConfigs {
        create("release") {
            val env: MutableMap<String, String> = System.getenv()
            keyAlias = env["KEY_ALIAS"]
            keyPassword = env["KEY_PASSWORD"]

            val base64keystore: String = env["KEYSTORE_BASE64"] ?: ""
            val keystoreFile: File = File.createTempFile("keystore", ".jks")
            keystoreFile.writeBytes(Base64.getDecoder().decode(base64keystore))
            storeFile = keystoreFile
            storePassword = env["KEYSTORE_PASSWORD"]
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}


tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val baseUrl = System.getenv("BASE_URL") ?: "https://github.com/timklge/karoo-wattspeed/releases/latest/download"
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "Touchpad",
            "packageName" to "de.timklge.karoowattspeed",
            "iconUrl" to "$baseUrl/karoo-touchpad.png",
            "latestApkUrl" to "$baseUrl/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "github.com/timklge",
            "description" to "Open-source extension that provides a virtual speedometer for indoor riding. Speed is calculated based on power, independant from the speed reported by the trainer..",
            "releaseNotes" to "* Initial commit",
            "screenshotUrls" to listOf(
                "$baseUrl/sensor.png",
                "$baseUrl/values.png",
            )
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")

        if (System.getenv()["BASE_URL"] != null){
            val androidManifestFile = file("$projectDir/src/main/AndroidManifest.xml")
            var androidManifestContent = androidManifestFile.readText()
            androidManifestContent = androidManifestContent.replace("\$BASE_URL\$", baseUrl)
            androidManifestFile.writeText(androidManifestContent)
            println("Replaced \$BASE_URL$ in AndroidManifest.xml")
        }
    }
}

tasks.named("assemble") {
    dependsOn("generateManifest")
}

// Ensure generateManifest runs before manifest processing
tasks.matching {
    it.name == "processDebugMainManifest" || it.name == "processReleaseMainManifest"
}.configureEach {
    dependsOn(tasks.named("generateManifest"))
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
}
