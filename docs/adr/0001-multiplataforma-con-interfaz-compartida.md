# 1. Kotlin Multiplatform con la interfaz también compartida

## Contexto

La app tiene que funcionar en Android y en escritorio con las mismas pantallas y la misma lógica. Con Kotlin
Multiplatform caben dos enfoques:

- Compartir solo el dominio y los datos, y escribir la interfaz de cada plataforma por separado (Jetpack Compose en
  Android y otra en escritorio).
- Compartir también la interfaz con Compose Multiplatform.

## Decisión

Se comparte todo en `commonMain`: dominio, datos (Ktor y SQLDelight), presentación (ViewModel y `StateFlow`) e
interfaz (Compose Multiplatform y Material 3). Lo específico de cada plataforma se queda en `expect/actual` o en
módulos de Koin:

- el driver de SQLite;
- el motor HTTP;
- la ubicación.

Android añade además lo que solo existe allí: WorkManager para los avisos y las notificaciones.

El proyecto sigue la estructura de AGP 9: la app de Android es un módulo propio (`androidApp`) que depende del módulo
KMP (`shared`, con el plugin `com.android.kotlin.multiplatform.library`).

## Alternativas descartadas

- **Interfaz por plataforma**: el doble de código de interfaz y dos sitios donde arreglar el mismo fallo.
- **Flutter**: otro lenguaje y otro proyecto del portfolio (`radar-movil`); aquí se trata de demostrar Kotlin.

## Consecuencias

- Una pantalla se prueba una vez (Compose UI Test en escritorio) y vale para las dos plataformas.
- Las pruebas comunes se ejecutan en JVM y en Android (`allTests`).
- iOS solo necesitaría su `actual` de ubicación, su driver y un punto de entrada, pero exige un Mac para compilar.
  Queda fuera, y se dice en el README.
