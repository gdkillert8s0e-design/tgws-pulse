import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

// The Flowseal core version is read from the lock file so the About screen always matches the bundled code.
val coreLock = rootProject.file("upstream/flowseal.lock.json")
val coreVersion = (JsonSlurper().parse(coreLock) as Map<*, *>)["core_version"].toString()

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env) ?: localProps.getProperty(prop)
val releaseKeystore: String? = signingValue("TGWS_KEYSTORE_FILE", "KEYSTORE_FILE")

android {
    namespace = "com.tgwsmobile.proxy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tgwsmobile.proxy"
        minSdk = 24
        targetSdk = 35
        versionCode = 13001
        versionName = "1.3.1"
        buildConfigField("String", "CORE_VERSION", "\"$coreVersion\"")
        ndk {
            // arm64 for current phones, armeabi-v7a for older (Android 7/8 era) 32-bit devices.
            // Chaquopy ships 32-bit runtimes only for Python 3.11 and older, hence the pinned version below.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("TGWS_KEYSTORE_PASSWORD", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("TGWS_KEY_ALIAS", "KEY_ALIAS")
                keyPassword = signingValue("TGWS_KEY_PASSWORD", "KEY_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = false
    }

    sourceSets.getByName("main") {
        // Notices shipped inside the APK: GPL-3.0 for the app, MIT for Flowseal, OFL for fonts.
        assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets").get().asFile)
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

chaquopy {
    defaultConfig {
        // 3.11 is the newest Python Chaquopy still builds for 32-bit (armeabi-v7a) devices.
        version = "3.11"
        (providers.gradleProperty("buildPython").orNull ?: localProps.getProperty("buildPython"))
            ?.let { buildPython(it) }
        pip {
            install("-r", "requirements-android.txt")
        }
    }
    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
            // Only the unmodified Flowseal proxy package (plus its locked Android AES loader) is packaged.
            srcDir(rootProject.file("upstream/flowseal"))
            exclude("tests/**", "LICENSE", "*.json", "*.md")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.webkit:webkit:1.12.0")

    testImplementation("junit:junit:4.13.2")
}

val licenseNotices by tasks.registering(Sync::class) {
    from(rootProject.file("LICENSE")) { rename { "APP-GPL-3.0.txt" } }
    from(rootProject.file("upstream/flowseal/LICENSE")) { rename { "Flowseal-MIT.txt" } }
    from(rootProject.file("licenses/fonts"))
    into(layout.buildDirectory.dir("generated/licenseAssets/licenses"))
}
tasks.named("preBuild") { dependsOn(licenseNotices) }
