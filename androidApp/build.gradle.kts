import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Firma de la versión publicada: las claves no están en el repositorio. Se leen de un fichero
// keystore.properties (storeFile, storePassword, keyAlias, keyPassword) en la raíz o en la ruta
// de SURTIDOR_FIRMA. Sin él, el APK de release sale sin firmar.
val firma =
    (System.getenv("SURTIDOR_FIRMA")?.let(::File) ?: rootProject.file("keystore.properties"))
        .takeIf { it.isFile }
        ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "es.surtidor.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "es.surtidor"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (firma != null) {
            create("publicada") {
                storeFile = file(firma.getProperty("storeFile"))
                storePassword = firma.getProperty("storePassword")
                keyAlias = firma.getProperty("keyAlias")
                keyPassword = firma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (firma != null) signingConfig = signingConfigs.getByName("publicada")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // Las versiones de las dependencias se revisan con Dependabot, no en cada compilación.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.work)
    implementation(libs.koin.android)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    testImplementation(kotlin("test"))
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.coroutines.test)
}
