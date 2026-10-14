package es.surtidor.datos.remoto

import es.surtidor.dominio.PreciosPublicados
import es.surtidor.dominio.Provincia
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

/** El Geoportal no responde o responde con un error. */
class ErrorDeRed(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa)

/** Cliente de la API REST de precios de carburantes del Ministerio (sin clave). */
class ClienteGeoportal(
    private val http: HttpClient,
) {
    suspend fun provincias(): List<Provincia> =
        pedir<List<ProvinciaDto>>("Listados/Provincias/").let { leer { it.map { p -> p.aDominio() } } }

    /** Los precios actuales de una provincia (código INE de dos cifras). */
    suspend fun preciosActuales(provinciaId: String): PreciosPublicados =
        pedir<RespuestaDto>("EstacionesTerrestres/FiltroProvincia/$provinciaId").let { leer { it.aDominio() } }

    /** Los precios que había un día pasado. */
    suspend fun preciosDelDia(
        provinciaId: String,
        dia: LocalDate,
    ): PreciosPublicados {
        val fecha = "${dos(dia.day)}-${dos(dia.month.ordinal + 1)}-${dia.year}"
        return pedir<RespuestaDto>("EstacionesTerrestresHist/FiltroProvincia/$fecha/$provinciaId").let {
            leer { it.aDominio() }
        }
    }

    private suspend inline fun <reified T> pedir(ruta: String): T {
        val respuesta: HttpResponse =
            try {
                http.get(ruta)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                throw ErrorDeRed("No se ha podido conectar con el Ministerio", e)
            }
        if (!respuesta.status.isSuccess()) {
            throw ErrorDeRed("El Ministerio ha respondido con un error (${respuesta.status.value})")
        }
        return try {
            respuesta.body()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ErrorDeRed("La respuesta del Ministerio no tiene el formato esperado", e)
        }
    }

    /** Una fecha con otro formato no tumba la app: es un error de la respuesta, como un JSON mal formado. */
    private inline fun <T> leer(convertir: () -> T): T =
        try {
            convertir()
        } catch (e: IllegalArgumentException) {
            throw ErrorDeRed("La respuesta del Ministerio no tiene el formato esperado", e)
        } catch (e: IndexOutOfBoundsException) {
            throw ErrorDeRed("La respuesta del Ministerio no tiene el formato esperado", e)
        }

    private fun dos(n: Int) = n.toString().padStart(2, '0')

    companion object {
        const val BASE = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/"

        /** El cliente HTTP de la app: uno solo, con tiempos de espera y reintentos ante fallos del servidor. */
        fun crearHttp(motor: io.ktor.client.engine.HttpClientEngine): HttpClient =
            HttpClient(motor) {
                expectSuccess = false
                install(ContentNegotiation) {
                    // La API responde JSON con «Content-Type: text/html» en algunos listados.
                    val json = Json { ignoreUnknownKeys = true }
                    json(json)
                    json(json, ContentType.Text.Html)
                    json(json, ContentType.Text.Plain)
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 15_000
                }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 2)
                    retryOnException(maxRetries = 2, retryOnTimeout = true)
                    exponentialDelay()
                }
                defaultRequest { url(BASE) }
            }
    }
}
