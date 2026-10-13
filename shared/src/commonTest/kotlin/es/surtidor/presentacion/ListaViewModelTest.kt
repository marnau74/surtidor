package es.surtidor.presentacion

import es.surtidor.MinisterioSimulado
import es.surtidor.Muestras
import es.surtidor.RelojDePrueba
import es.surtidor.baseDePrueba
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Coordenadas
import es.surtidor.dominio.Orden
import es.surtidor.esperarA
import es.surtidor.json
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ListaViewModelTest {
    private val principal = StandardTestDispatcher()
    private var sinConexion = false
    private val ministerio =
        MinisterioSimulado { peticion ->
            when {
                sinConexion -> json("", HttpStatusCode.ServiceUnavailable)

                peticion.url.encodedPath.contains(
                    "Listados/Provincias",
                ) -> json(Muestras.PROVINCIAS, tipo = "text/html")

                else -> json(Muestras.ACTUAL_MURCIA)
            }
        }

    @BeforeTest
    fun antes() = Dispatchers.setMain(principal)

    @AfterTest
    fun despues() = Dispatchers.resetMain()

    private val base = baseDePrueba()

    private fun TestScope.crear(): Pair<ListaViewModel, () -> EstadoLista> {
        val repo = RepositorioDeGasolineras(ministerio.cliente, base, RelojDePrueba(), principal)
        val vm = ListaViewModel(repo)
        // El estado solo se calcula con alguien escuchando (como la pantalla).
        backgroundScope.launch(principal) { vm.estado.collect {} }
        return vm to { vm.estado.value }
    }

    @Test
    fun alAbrirCargaMurciaOrdenadaPorPrecio() =
        runTest(principal) {
            val (_, estado) = crear()
            esperarA { !estado().cargando && estado().publicadosEn != null }
            val e = estado()
            assertFalse(e.cargando)
            assertEquals("Murcia", e.provincia?.nombre)
            assertEquals(Carburante.GASOLINA_95, e.seleccion.carburante)
            val precios = e.gasolineras.map { it.precio }
            assertEquals(precios.sorted(), precios)
            assertTrue(e.gasolineras.none { it.gasolinera.id == 13177L }, "la 13177 no vende gasolina 95")
            assertNotNull(e.resumen)
        }

    @Test
    fun cambiarDeCarburanteFiltraYSeRecuerda() =
        runTest(principal) {
            val (vm, estado) = crear()
            esperarA { estado().publicadosEn != null }
            vm.elegirCarburante(Carburante.GLP)
            advanceUntilIdle()
            assertEquals(setOf(13177L, 2162L), estado().gasolineras.map { it.gasolinera.id }.toSet())

            // Al volver a abrir la app (otro ViewModel, misma base), sigue elegido.
            val (_, otraVez) = crear()
            esperarA { otraVez().publicadosEn != null }
            assertEquals(Carburante.GLP, otraVez().seleccion.carburante)
        }

    @Test
    fun conUbicacionOrdenaPorCercania() =
        runTest(principal) {
            val (vm, estado) = crear()
            esperarA { estado().publicadosEn != null }
            vm.ordenarPor(Orden.DISTANCIA)
            assertEquals(Orden.PRECIO, estado().seleccion.orden, "sin ubicación no se puede ordenar por cercanía")
            vm.usarUbicacion(Coordenadas(38.2, -1.3)) // Abarán
            advanceUntilIdle()
            val e = estado()
            assertEquals(Orden.DISTANCIA, e.seleccion.orden)
            val distancias = e.gasolineras.mapNotNull { it.distanciaKm }
            assertEquals(distancias.sorted(), distancias)
            assertEquals(
                "Abarán",
                e.gasolineras
                    .first()
                    .gasolinera.municipio,
            )
        }

    @Test
    fun sinConexionLoDiceSinRomperse() =
        runTest(principal) {
            sinConexion = true
            val (vm, estado) = crear()
            esperarA { estado().error != null && !estado().cargando }
            val e = estado()
            assertNotNull(e.error)
            assertTrue(e.vacio)
            vm.descartarError()
            advanceUntilIdle()
            assertEquals(null, estado().error)
        }

    @Test
    fun buscarFiltraPorMunicipioSinTildes() =
        runTest(principal) {
            val (vm, estado) = crear()
            esperarA { estado().publicadosEn != null }
            vm.elegirCarburante(Carburante.GASOLEO_A)
            vm.buscar("aguilas")
            advanceUntilIdle()
            assertTrue(estado().gasolineras.isNotEmpty())
            assertTrue(estado().gasolineras.all { it.gasolinera.municipio == "Águilas" })
        }
}
