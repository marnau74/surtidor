# Cambios

Formato de [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versiones con
[versionado semántico](https://semver.org/lang/es/).

## [0.1.0] - 2026-10-13

Primera versión.

### Añadido

- App de Android y escritorio con Kotlin Multiplatform y Compose Multiplatform.
- Precios por provincia y carburante del Geoportal del Ministerio, ordenados por precio o por cercanía, con la
  diferencia frente a la media.
- Búsqueda sin tildes.
- Ficha con todos los precios, horario, «Cómo llegar» y evolución de 30 días.
- Funcionamiento sin conexión con SQLite (SQLDelight).
- Favoritas con aviso de precio: WorkManager y notificaciones en Android; al abrir la app en escritorio.
- Pruebas comunes (JVM y Android), pruebas de interfaz en escritorio, ktlint y Android Lint.
- CI escrita para GitHub Actions.
