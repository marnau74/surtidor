package es.surtidor

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import es.surtidor.datos.local.BaseDeDatos

actual fun driverDePrueba(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
        BaseDeDatos.Schema.create(it)
    }
