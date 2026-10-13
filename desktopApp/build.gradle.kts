import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.koin.core)
}

compose.desktop {
    application {
        mainClass = "es.surtidor.escritorio.MainKt"
        // El JDK que empaqueta tiene que traer jpackage (el de Android Studio no lo trae): -PjdkEmpaquetado=<ruta>.
        providers.gradleProperty("jdkEmpaquetado").orNull?.let { javaHome = it }
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Surtidor"
            packageVersion = "1.0.0"
            description = "Precios de los carburantes en España"
            vendor = "Mario Arnau"
            modules("java.sql")
            windows { menuGroup = "Surtidor" }
        }
    }
}
