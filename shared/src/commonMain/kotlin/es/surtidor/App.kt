package es.surtidor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import es.surtidor.presentacion.DetalleViewModel
import es.surtidor.presentacion.FavoritasViewModel
import es.surtidor.presentacion.ListaViewModel
import es.surtidor.ui.AccionesDetalle
import es.surtidor.ui.AccionesLista
import es.surtidor.ui.Iconos
import es.surtidor.ui.PantallaDetalle
import es.surtidor.ui.PantallaFavoritas
import es.surtidor.ui.PantallaLista
import es.surtidor.ui.TemaSurtidor
import es.surtidor.ui.recordarPeticionDeUbicacion
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.reflect.KClass

@Serializable
object RutaLista

@Serializable
object RutaFavoritas

@Serializable
data class RutaDetalle(
    val id: Long,
)

private data class Destino(
    val ruta: Any,
    val clase: KClass<*>,
    val nombre: String,
    val icono: ImageVector,
)

private val DESTINOS =
    listOf(
        Destino(RutaLista, RutaLista::class, "Gasolineras", Iconos.Gasolinera),
        Destino(RutaFavoritas, RutaFavoritas::class, "Favoritas", Iconos.Estrella),
    )

/** La app entera. En pantallas anchas (escritorio, tableta) la navegación va en un riel lateral. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(navegacion: NavHostController = rememberNavController()) {
    TemaSurtidor {
        val entrada by navegacion.currentBackStackEntryAsState()
        // Con hasRoute y no con el nombre de la clase: en la versión minimizada (R8) los nombres se ofuscan.
        val enDetalle = entrada?.destination?.hasRoute(RutaDetalle::class) == true
        val esActual: (Destino) -> Boolean = { d ->
            entrada?.destination?.hierarchy?.any { it.hasRoute(d.clase) } ==
                true
        }
        val irA: (Any) -> Unit = { ruta ->
            navegacion.navigate(ruta) {
                popUpTo(RutaLista) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ancha = maxWidth >= 720.dp
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Surtidor") },
                        navigationIcon = {
                            if (enDetalle) {
                                IconButton(onClick = { navegacion.popBackStack() }) {
                                    Icon(Iconos.Atras, contentDescription = "Volver")
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!ancha) {
                        NavigationBar {
                            DESTINOS.forEach { d ->
                                NavigationBarItem(
                                    selected = esActual(d),
                                    onClick = { irA(d.ruta) },
                                    icon = { Icon(d.icono, contentDescription = null) },
                                    label = { Text(d.nombre) },
                                )
                            }
                        }
                    }
                },
            ) { relleno ->
                Row(Modifier.padding(relleno).fillMaxSize()) {
                    if (ancha) {
                        NavigationRail {
                            DESTINOS.forEach { d ->
                                NavigationRailItem(
                                    selected = esActual(d),
                                    onClick = { irA(d.ruta) },
                                    icon = { Icon(d.icono, contentDescription = null) },
                                    label = { Text(d.nombre) },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        Navegacion(navegacion, Modifier.widthIn(max = 900.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Navegacion(
    navegacion: NavHostController,
    modifier: Modifier,
) {
    NavHost(navegacion, startDestination = RutaLista, modifier = modifier) {
        composable<RutaLista> {
            val vm = koinViewModel<ListaViewModel>()
            val estado by vm.estado.collectAsStateWithLifecycle()
            val pedirUbicacion = recordarPeticionDeUbicacion(vm::usarUbicacion)
            PantallaLista(
                estado,
                AccionesLista(
                    elegirProvincia = vm::elegirProvincia,
                    elegirCarburante = vm::elegirCarburante,
                    ordenarPor = vm::ordenarPor,
                    buscar = vm::buscar,
                    refrescar = { vm.refrescar(forzar = true) },
                    pedirUbicacion = pedirUbicacion,
                    abrir = { navegacion.navigate(RutaDetalle(it)) },
                    descartarError = vm::descartarError,
                ),
            )
        }
        composable<RutaDetalle> { entrada ->
            val id = entrada.toRoute<RutaDetalle>().id
            val vm = koinViewModel<DetalleViewModel>(key = "detalle-$id") { parametersOf(id) }
            val estado by vm.estado.collectAsStateWithLifecycle()
            PantallaDetalle(
                estado,
                AccionesDetalle(
                    elegirCarburante = vm::elegirCarburante,
                    alternarFavorita = vm::alternarFavorita,
                    fijarUmbral = vm::fijarUmbral,
                    reintentarHistorico = vm::reintentarHistorico,
                ),
            )
        }
        composable<RutaFavoritas> {
            val vm = koinViewModel<FavoritasViewModel>()
            val favoritas by vm.favoritas.collectAsStateWithLifecycle()
            val avisos by vm.avisosNuevos.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { vm.comprobarAvisos() }
            PantallaFavoritas(
                favoritas,
                avisos,
                abrir = { navegacion.navigate(RutaDetalle(it)) },
                quitar = vm::quitar,
                descartarAvisos = vm::descartarAvisos,
            )
        }
    }
}
