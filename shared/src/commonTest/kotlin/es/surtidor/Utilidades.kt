package es.surtidor

import app.cash.sqldelight.db.SqlDriver
import es.surtidor.datos.local.BaseDeDatos
import es.surtidor.datos.remoto.ClienteGeoportal
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.time.Clock
import kotlin.time.Instant

/** Una base SQLite en memoria con el esquema creado (cada plataforma pone su driver). */
expect fun driverDePrueba(): SqlDriver

fun baseDePrueba(): BaseDeDatos = BaseDeDatos(driverDePrueba())

/** Un reloj que solo avanza cuando el test lo dice. */
class RelojDePrueba(
    var ahora: Instant = Instant.parse("2026-10-13T18:00:00Z"),
) : Clock {
    override fun now(): Instant = ahora
}

/**
 * Espera (en tiempo real, no virtual) a que se cumpla [condicion]. El motor HTTP simulado de Ktor no corre en el
 * planificador del test, así que avanzar el reloj virtual no basta para que lleguen sus respuestas.
 */
suspend fun kotlinx.coroutines.test.TestScope.esperarA(
    maximoMs: Long = 15_000,
    condicion: () -> Boolean,
) {
    val inicio =
        kotlin.time.TimeSource.Monotonic
            .markNow()
    while (true) {
        testScheduler.advanceUntilIdle()
        if (condicion()) return
        check(inicio.elapsedNow().inWholeMilliseconds < maximoMs) { "No se ha cumplido la condición en $maximoMs ms" }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { kotlinx.coroutines.delay(10) }
    }
}

/** Un Ministerio simulado: responde según la ruta pedida y anota todas las peticiones. */
class MinisterioSimulado(
    private val respuesta: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val peticiones = mutableListOf<String>()
    val motor =
        MockEngine { peticion ->
            peticiones += peticion.url.encodedPath
            respuesta(peticion)
        }
    val cliente = ClienteGeoportal(ClienteGeoportal.crearHttp(motor))
}

fun MockRequestHandleScope.json(
    cuerpo: String,
    estado: HttpStatusCode = HttpStatusCode.OK,
    tipo: String = "application/json; charset=utf-8",
): HttpResponseData = respond(cuerpo, estado, headersOf(HttpHeaders.ContentType, tipo))
