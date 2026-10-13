import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        namespace = "es.surtidor.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
        withHostTestBuilder {}
    }

    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.navigation.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            api(libs.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android)
            implementation(libs.coroutines.android)
            implementation(libs.koin.android)
            implementation(libs.androidx.activity.compose)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.jvm)
        }
        getByName("desktopMain").dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.jvm)
            implementation(libs.coroutines.swing)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(libs.sqldelight.jvm)
        }
    }
}

sqldelight {
    databases {
        create("BaseDeDatos") {
            packageName.set("es.surtidor.datos.local")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/esquemas"))
            verifyMigrations.set(true)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "es.surtidor.recursos"
}

// Capturas del README (ver CapturasTest): ./gradlew :shared:desktopTest -Pcapturas=<carpeta con los JSON>
tasks.named<Test>("desktopTest") {
    val capturas = providers.gradleProperty("capturas").orElse("")
    val salida =
        rootProject.layout.projectDirectory
            .dir("docs/capturas")
            .asFile.absolutePath
    inputs.property("capturas", capturas)
    doFirst {
        systemProperty("surtidor.capturas", capturas.get())
        systemProperty("surtidor.salidaCapturas", salida)
    }
}
