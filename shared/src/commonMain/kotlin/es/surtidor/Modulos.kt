package es.surtidor

import app.cash.sqldelight.db.SqlDriver
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.datos.ServicioDeAvisos
import es.surtidor.datos.local.BaseDeDatos
import es.surtidor.datos.remoto.ClienteGeoportal
import es.surtidor.presentacion.DetalleViewModel
import es.surtidor.presentacion.FavoritasViewModel
import es.surtidor.presentacion.ListaViewModel
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock

/** Lo que pone cada plataforma: el motor HTTP y el driver de SQLite. */
expect val moduloDePlataforma: Module

val moduloComun =
    module {
        single { ClienteGeoportal(ClienteGeoportal.crearHttp(get<HttpClientEngine>())) }
        single { BaseDeDatos(get<SqlDriver>()) }
        single<Clock> { Clock.System }
        single { RepositorioDeGasolineras(get(), get(), get(), Dispatchers.IO) }
        single { ServicioDeAvisos(get()) }
        viewModel { ListaViewModel(get()) }
        viewModel { (id: Long) -> DetalleViewModel(id, get()) }
        viewModel { FavoritasViewModel(get(), get()) }
    }
