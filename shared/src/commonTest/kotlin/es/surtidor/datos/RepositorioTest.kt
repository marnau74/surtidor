package es.surtidor.datos

import es.surtidor.MinisterioSimulado
import es.surtidor.Muestras
import es.surtidor.RelojDePrueba
import es.surtidor.baseDePrueba
import es.surtidor.datos.remoto.ErrorDeRed
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Favorita
import es.surtidor.json
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RepositorioTest {
    private val reloj = RelojDePrueba(Instant.parse("2026-10-13T18:00:00Z"))
    private var fallar = false

    /**
     * Responde los precios actuales y, para el histórico, la muestra del 1 de octubre salvo el día 2 (falla) y el 12
     * (llega vacío, como un día que el Ministerio aún no ha publicado).
     */
    private val ministerio =
        MinisterioSimulado { peticion ->
            val ruta = peticion.url.encodedPath
            when {
                fallar -> {
                    json("", HttpStatusCode.InternalServerError)
                }

                ruta.contains("Hist/FiltroProvincia/02-10-2026") -> {
                    json("", HttpStatusCode.InternalServerError)
                }

                ruta.contains("Hist/FiltroProvincia/12-10-2026") -> {
                    json("""{"Fecha":"12/10/2026 0:00:00","ListaEESSPrecio":[]}""")
                }

                ruta.contains("Hist/FiltroProvincia/") -> {
                    val fecha = ruta.substringAfter("FiltroProvincia/").substringBefore("/")
                    json(Muestras.HISTORICO_MURCIA_1_OCTUBRE.replace("01/10/2026", fecha.replace('-', '/')))
                }

                else -> {
                    json(Muestras.ACTUAL_MURCIA)
                }
            }
        }

    private fun TestScope.repositorio() =
        RepositorioDeGasolineras(ministerio.cliente, baseDePrueba(), reloj, StandardTestDispatcher(testScheduler))

    @Test
    fun guardaLaProvinciaYLaEnsenaDesdeLaBase() =
        runTest {
            val repo = repositorio()
            assertNull(repo.datosDeProvincia("30").first())
            repo.refrescar("30")
            val datos = assertNotNull(repo.datosDeProvincia("30").first())
            assertEquals(Instant.parse("2026-10-13T17:45:20Z"), datos.publicadosEn)
            assertEquals(5, datos.gasolineras.size)
            assertEquals(1.909, datos.gasolineras.first { it.id == 4149L }.precios[Carburante.GASOLEO_A])
        }

    @Test
    fun noVuelveAPedirLoQueTieneMenosDeMediaHora() =
        runTest {
            val repo = repositorio()
            repo.refrescar("30")
            reloj.ahora += 29.minutes
            repo.refrescar("30")
            assertEquals(1, ministerio.peticiones.size)
            reloj.ahora += 2.minutes
            repo.refrescar("30")
            assertEquals(2, ministerio.peticiones.size)
            repo.refrescar("30", forzar = true)
            assertEquals(3, ministerio.peticiones.size)
        }

    @Test
    fun sinConexionSeConservaLoGuardado() =
        runTest {
            val repo = repositorio()
            repo.refrescar("30")
            fallar = true
            assertFailsWith<ErrorDeRed> { repo.refrescar("30", forzar = true) }
            assertEquals(
                5,
                repo
                    .datosDeProvincia("30")
                    .first()
                    ?.gasolineras
                    ?.size,
            )
        }

    @Test
    fun elHistoricoPideSoloLosDiasQueFaltanYSaltaLosQueFallan() =
        runTest {
            val repo = repositorio()
            repo.refrescar("30")
            val g = repo.gasolinerasDeProvinciaAhora("30").first { it.id == 4149L }
            val historico = repo.historico(g, Carburante.GASOLEO_A, dias = 30)

            // Los precios de hoy (al refrescar) y los 29 días anteriores, del 14 de septiembre al 12 de octubre: 27 bien,
            // el 12 vacío y el 2 de octubre, que falla, con sus dos reintentos.
            assertEquals(1 + 28 + 3, ministerio.peticiones.size)
            assertTrue(LocalDate(2026, 10, 2) !in historico.map { it.fecha }, "el día que falló no se inventa")
            assertTrue(LocalDate(2026, 10, 12) !in historico.map { it.fecha })
            assertEquals(28, historico.size)
            assertEquals(LocalDate(2026, 10, 13), historico.last().fecha)

            val antes = ministerio.peticiones.size
            repo.historico(g, Carburante.GASOLEO_A, dias = 30)
            assertEquals(
                antes + 3 + 1,
                ministerio.peticiones.size,
                "la segunda vez solo se vuelven a pedir el día que falló y el que llegó vacío",
            )
        }

    @Test
    fun lasFavoritasLlevanSuPrecioActual() =
        runTest {
            val repo = repositorio()
            repo.refrescar("30")
            repo.guardarFavorita(Favorita(4149, Carburante.GASOLEO_A, umbral = 1.95))
            val favorita = repo.favoritas().first().single()
            assertEquals(1.909, favorita.gasolinera?.precios?.get(Carburante.GASOLEO_A))
            assertEquals(1.95, favorita.favorita.umbral)
            repo.quitarFavorita(4149)
            assertTrue(repo.favoritas().first().isEmpty())
        }

    @Test
    fun losAjustesSeRecuerdan() =
        runTest {
            val repo = repositorio()
            assertNull(repo.leerAjuste("provincia"))
            repo.guardarAjuste("provincia", "15")
            assertEquals("15", repo.leerAjuste("provincia"))
        }

    @Test
    fun avisaUnaVezYOtraSiVuelveABajarTrasSubir() =
        runTest {
            val repo = repositorio()
            val avisos = ServicioDeAvisos(repo)
            repo.refrescar("30")
            repo.guardarFavorita(Favorita(4149, Carburante.GASOLEO_A, umbral = 1.95))

            assertEquals(1, avisos.comprobar().size)
            assertTrue(avisos.comprobar().isEmpty(), "mismo precio: no se repite")

            repo.guardarFavorita(Favorita(4149, Carburante.GASOLEO_A, umbral = 1.80))
            assertTrue(avisos.comprobar().isEmpty(), "1,909 está por encima de 1,80: se olvida el aviso anterior")
            repo.guardarFavorita(Favorita(4149, Carburante.GASOLEO_A, umbral = 1.95))
            assertEquals(1, avisos.comprobar().size, "vuelve a estar por debajo: se avisa otra vez")
        }
}
