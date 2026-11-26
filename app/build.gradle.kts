import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xuleo.xmplus.md3"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.xuleo.xmplus.md3"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.0"
    }

    androidResources {
        generateLocaleConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The release key lives outside the repository: keystore.properties on a developer machine, or the XM_* variables in CI.
    // Without either, assembleRelease still builds, but the APK is unsigned and cannot be installed.
    val keyFile = rootProject.file("keystore.properties")
    val keyProperties = Properties().apply { if (keyFile.exists()) keyFile.inputStream().use { load(it) } }
    fun releaseKey(property: String, variable: String): String? = keyProperties.getProperty(property) ?: System.getenv(variable)
    signingConfigs {
        val store = releaseKey("storeFile", "XM_KEYSTORE_FILE")
        if (store != null) create("release") {
            storeFile = rootProject.file(store)
            storePassword = releaseKey("storePassword", "XM_STORE_PASSWORD")
            keyAlias = releaseKey("keyAlias", "XM_KEY_ALIAS")
            keyPassword = releaseKey("keyPassword", "XM_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        abortOnError = true
        checkDependencies = true
        error += setOf("MissingTranslation", "ExtraTranslation")
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:account"))
    implementation(project(":core:data"))
    implementation(project(":core:protocol"))
    implementation(project(":core:media"))
    implementation(project(":core:download"))
    implementation(project(":core:plugin"))
    implementation(libs.okhttp)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.webkit)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.compose.ui.tooling)
}
