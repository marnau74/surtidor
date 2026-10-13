package es.surtidor

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import es.surtidor.datos.local.BaseDeDatos
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val moduloDePlataforma =
    module {
        single<SqlDriver> { AndroidSqliteDriver(BaseDeDatos.Schema, androidContext(), "surtidor.db") }
        single<HttpClientEngine> { OkHttp.create() }
    }
