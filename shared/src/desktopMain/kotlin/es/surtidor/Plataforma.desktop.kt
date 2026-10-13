package es.surtidor

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.surtidor.datos.local.BaseDeDatos
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module
import java.io.File
import java.util.Properties

/** La base de datos va en la carpeta del usuario (`~/.surtidor`), la misma en Windows, macOS y Linux. */
private fun rutaDeLaBase(): File =
    File(System.getProperty("user.home"), ".surtidor")
        .apply {
            mkdirs()
        }.resolve("surtidor.db")

actual val moduloDePlataforma =
    module {
        single<SqlDriver> {
            JdbcSqliteDriver(
                "jdbc:sqlite:${rutaDeLaBase().absolutePath}",
                Properties(),
                BaseDeDatos.Schema,
            )
        }
        single<HttpClientEngine> { OkHttp.create() }
    }
