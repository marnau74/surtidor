package es.surtidor.presentacion

import es.surtidor.MinisterioSimulado
import es.surtidor.Muestras
import es.surtidor.RelojDePrueba
import es.surtidor.baseDePrueba
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Favorita
import es.surtidor.esperarA
import es.surtidor.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DetalleViewModelTest {
    private val principal = StandardTestDispatcher()
    private val ministerio = MinisterioSimulado { json(Muestras.ACTUAL_MURCIA) }

    @BeforeTest
    fun antes() = Dispatchers.setMain(principal)

    @AfterTest
    fun despues() = Dispatchers.resetMain()

    @Test
    fun elUmbralEsDelCarburanteDeLaFavoritaAunqueSeEsteMirandoOtro() =
        runTest(principal) {
            val repo = RepositorioDeGasolineras(ministerio.cliente, baseDePrueba(), RelojDePrueba(), principal)
            repo.refrescar("30")
            repo.guardarFavorita(Favorita(4149L, Carburante.GASOLEO_A, null))
            val vm = DetalleViewModel(4149L, repo)
            esperarA { vm.estado.value.favorita != null && !vm.estado.value.cargando }

            vm.elegirCarburante(Carburante.GASOLINA_95)
            vm.fijarUmbral(1.85)
            esperarA {
                vm.estado.value.favorita
                    ?.umbral == 1.85
            }

            assertEquals(
                Favorita(4149L, Carburante.GASOLEO_A, 1.85),
                repo
                    .favoritas()
                    .first()
                    .single()
                    .favorita,
            )
        }
}
