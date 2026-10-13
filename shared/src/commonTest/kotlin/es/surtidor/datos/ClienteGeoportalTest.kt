package es.surtidor.datos

import es.surtidor.MinisterioSimulado
import es.surtidor.Muestras
import es.surtidor.datos.remoto.ErrorDeRed
import es.surtidor.json
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClienteGeoportalTest {
    @Test
    fun pideLosPreciosDeLaProvincia() =
        runTest {
            val ministerio = MinisterioSimulado { json(Muestras.ACTUAL_MURCIA) }
            val precios = ministerio.cliente.preciosActuales("30")
            assertEquals(5, precios.gasolineras.size)
            assertTrue(
                ministerio.peticiones.single().endsWith("/PreciosCarburantes/EstacionesTerrestres/FiltroProvincia/30"),
            )
        }

    @Test
    fun elHistoricoSePideConLaFechaEnFormatoEspanol() =
        runTest {
            val ministerio = MinisterioSimulado { json(Muestras.HISTORICO_MURCIA_1_OCTUBRE) }
            ministerio.cliente.preciosDelDia("30", LocalDate(2026, 10, 1))
            assertTrue(
                ministerio.peticiones.single().endsWith("/EstacionesTerrestresHist/FiltroProvincia/01-10-2026/30"),
            )
        }

    @Test
    fun aceptaJsonServidoComoHtml() =
        runTest {
            // El listado de provincias llega con «Content-Type: text/html».
            val ministerio = MinisterioSimulado { json(Muestras.PROVINCIAS, tipo = "text/html") }
            val provincias = ministerio.cliente.provincias()
            assertEquals("A Coruña", provincias.first { it.id == "15" }.nombre)
            assertEquals("Las Palmas", provincias.first { it.id == "35" }.nombre)
        }

    @Test
    fun unErrorDelServidorSeReintentaYAcabaEnErrorDeRed() =
        runTest {
            val ministerio = MinisterioSimulado { json("", HttpStatusCode.ServiceUnavailable) }
            val error = assertFailsWith<ErrorDeRed> { ministerio.cliente.preciosActuales("30") }
            assertTrue(error.message!!.contains("503"))
            assertEquals(3, ministerio.peticiones.size, "la petición y dos reintentos")
        }

    @Test
    fun unaRespuestaConOtroFormatoEsUnErrorDeRedYNoUnFallo() =
        runTest {
            val ministerio = MinisterioSimulado { json("<html>Mantenimiento</html>") }
            assertFailsWith<ErrorDeRed> { ministerio.cliente.preciosActuales("30") }
        }
}
