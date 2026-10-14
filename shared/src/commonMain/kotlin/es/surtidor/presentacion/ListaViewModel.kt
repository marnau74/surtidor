package es.surtidor.presentacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.surtidor.datos.DatosDeProvincia
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.datos.remoto.ErrorDeRed
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Coordenadas
import es.surtidor.dominio.GasolineraEnLista
import es.surtidor.dominio.Orden
import es.surtidor.dominio.Provincia
import es.surtidor.dominio.Resumen
import es.surtidor.dominio.ordenar
import es.surtidor.dominio.resumir
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant

/** Lo que el usuario ha elegido. La provincia y el carburante se recuerdan entre sesiones. */
data class Seleccion(
    val provinciaId: String? = null,
    val carburante: Carburante = Carburante.GASOLINA_95,
    val orden: Orden = Orden.PRECIO,
    val texto: String = "",
    val ubicacion: Coordenadas? = null,
)

data class EstadoLista(
    val seleccion: Seleccion = Seleccion(),
    val provincias: List<Provincia> = emptyList(),
    val gasolineras: List<GasolineraEnLista> = emptyList(),
    val resumen: Resumen? = null,
    val publicadosEn: Instant? = null,
    val cargando: Boolean = true,
    val error: String? = null,
) {
    val provincia: Provincia? get() = provincias.firstOrNull { it.id == seleccion.provinciaId }

    /** Sin nada guardado todavía (primera vez sin conexión, por ejemplo). */
    val vacio: Boolean get() = !cargando && publicadosEn == null
}

private data class Refresco(
    val cargando: Boolean = false,
    val error: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListaViewModel(
    private val repositorio: RepositorioDeGasolineras,
) : ViewModel() {
    private val seleccion = MutableStateFlow(Seleccion())
    private val refresco = MutableStateFlow(Refresco(cargando = true))
    private val provincias = MutableStateFlow<List<Provincia>>(emptyList())
    private var refrescoEnCurso: Job? = null

    private val datos =
        seleccion
            .map { it.provinciaId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else repositorio.datosDeProvincia(id) }

    val estado: StateFlow<EstadoLista> =
        combine(seleccion, refresco, provincias, datos) { sel, ref, lista, datos: DatosDeProvincia? ->
            val ordenadas =
                datos
                    ?.let {
                        ordenar(it.gasolineras, sel.carburante, sel.orden, sel.ubicacion, sel.texto)
                    }.orEmpty()
            EstadoLista(
                seleccion = sel,
                provincias = lista,
                gasolineras = ordenadas,
                resumen = datos?.let { d -> resumir(d.gasolineras.mapNotNull { it.precios[sel.carburante] }) },
                publicadosEn = datos?.publicadosEn,
                cargando = ref.cargando,
                error = ref.error,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoLista())

    init {
        viewModelScope.launch {
            val provincia = repositorio.leerAjuste(AJUSTE_PROVINCIA) ?: PROVINCIA_INICIAL
            val carburante =
                repositorio.leerAjuste(AJUSTE_CARBURANTE)?.let { runCatching { Carburante.valueOf(it) }.getOrNull() }
                    ?: Carburante.GASOLINA_95
            seleccion.update { it.copy(provinciaId = provincia, carburante = carburante) }
            try {
                provincias.value = repositorio.provincias()
            } catch (e: ErrorDeRed) {
                refresco.update { it.copy(error = e.message) }
            }
            refrescar()
        }
    }

    fun refrescar(forzar: Boolean = false) {
        val provincia = seleccion.value.provinciaId ?: return
        // Si se cambia de provincia a media descarga, la anterior ya no importa: que no pise el estado de la nueva.
        refrescoEnCurso?.cancel()
        refrescoEnCurso =
            viewModelScope.launch {
                refresco.value = Refresco(cargando = true)
                refresco.value =
                    try {
                        repositorio.refrescar(provincia, forzar)
                        if (provincias.value.isEmpty()) provincias.value = repositorio.provincias()
                        Refresco()
                    } catch (e: ErrorDeRed) {
                        Refresco(error = e.message)
                    }
            }
    }

    fun elegirProvincia(id: String) {
        if (id == seleccion.value.provinciaId) return
        // La ubicación es de la provincia anterior: al cambiar, se ordena por precio.
        seleccion.update { it.copy(provinciaId = id, ubicacion = null, orden = Orden.PRECIO, texto = "") }
        viewModelScope.launch { repositorio.guardarAjuste(AJUSTE_PROVINCIA, id) }
        refrescar()
    }

    fun elegirCarburante(carburante: Carburante) {
        seleccion.update { it.copy(carburante = carburante) }
        viewModelScope.launch { repositorio.guardarAjuste(AJUSTE_CARBURANTE, carburante.name) }
    }

    fun ordenarPor(orden: Orden) {
        if (orden == Orden.DISTANCIA && seleccion.value.ubicacion == null) return
        seleccion.update { it.copy(orden = orden) }
    }

    fun buscar(texto: String) = seleccion.update { it.copy(texto = texto) }

    /** La ubicación del usuario (o nula si no la ha dado): con ella se ordena por cercanía. */
    fun usarUbicacion(coordenadas: Coordenadas?) {
        if (coordenadas == null) {
            refresco.update { it.copy(error = "No se ha podido obtener tu ubicación") }
            return
        }
        seleccion.update { it.copy(ubicacion = coordenadas, orden = Orden.DISTANCIA) }
    }

    fun descartarError() = refresco.update { it.copy(error = null) }

    companion object {
        const val AJUSTE_PROVINCIA = "provincia"
        const val AJUSTE_CARBURANTE = "carburante"
        const val PROVINCIA_INICIAL = "30"
    }
}
