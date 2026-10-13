package es.surtidor.presentacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Favorita
import es.surtidor.dominio.Gasolinera
import es.surtidor.dominio.PrecioDelDia
import es.surtidor.dominio.resumir
import es.surtidor.presentacion.ListaViewModel.Companion.AJUSTE_CARBURANTE
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface EstadoHistorico {
    data object Cargando : EstadoHistorico

    data class Datos(
        val precios: List<PrecioDelDia>,
    ) : EstadoHistorico

    data object Error : EstadoHistorico
}

data class EstadoDetalle(
    val cargando: Boolean = true,
    val gasolinera: Gasolinera? = null,
    val carburante: Carburante = Carburante.GASOLINA_95,
    /** Media del carburante en la provincia, para dar contexto al precio. */
    val mediaProvincia: Double? = null,
    val historico: EstadoHistorico = EstadoHistorico.Cargando,
    val favorita: Favorita? = null,
) {
    val precio: Double? get() = gasolinera?.precios?.get(carburante)
}

class DetalleViewModel(
    private val gasolineraId: Long,
    private val repositorio: RepositorioDeGasolineras,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoDetalle())
    val estado: StateFlow<EstadoDetalle> = _estado.asStateFlow()
    private var cargaDelHistorico: Job? = null

    init {
        viewModelScope.launch {
            val gasolinera = repositorio.gasolinerasConUltimoPrecio(listOf(gasolineraId))[gasolineraId]
            val preferido =
                repositorio.leerAjuste(AJUSTE_CARBURANTE)?.let { runCatching { Carburante.valueOf(it) }.getOrNull() }
            // El carburante preferido si la gasolinera lo vende; si no, el primero que venda.
            val carburante =
                preferido?.takeIf { gasolinera?.precios?.containsKey(it) == true }
                    ?: gasolinera?.precios?.keys?.minByOrNull { it.ordinal }
                    ?: Carburante.GASOLINA_95
            _estado.update { it.copy(cargando = false, gasolinera = gasolinera, carburante = carburante) }
            if (gasolinera != null) cargar(carburante)
        }
        viewModelScope.launch {
            repositorio.esFavorita(gasolineraId).collectLatest { favorita ->
                _estado.update { it.copy(favorita = favorita) }
            }
        }
    }

    fun elegirCarburante(carburante: Carburante) {
        if (_estado.value.gasolinera
                ?.precios
                ?.containsKey(carburante) != true
        ) {
            return
        }
        _estado.update { it.copy(carburante = carburante) }
        cargar(carburante)
    }

    fun reintentarHistorico() = cargar(_estado.value.carburante)

    fun alternarFavorita() {
        val actual = _estado.value
        viewModelScope.launch {
            if (actual.favorita != null) {
                repositorio.quitarFavorita(gasolineraId)
            } else {
                repositorio.guardarFavorita(Favorita(gasolineraId, actual.carburante, null))
            }
        }
    }

    /** Avisar cuando el precio del carburante elegido baje de [umbral] (nulo: sin aviso). */
    fun fijarUmbral(umbral: Double?) {
        val actual = _estado.value
        viewModelScope.launch {
            repositorio.guardarFavorita(Favorita(gasolineraId, actual.carburante, umbral?.takeIf { it > 0 }))
        }
    }

    private fun cargar(carburante: Carburante) {
        val gasolinera = _estado.value.gasolinera ?: return
        cargaDelHistorico?.cancel()
        cargaDelHistorico =
            viewModelScope.launch {
                _estado.update { it.copy(historico = EstadoHistorico.Cargando) }
                val media =
                    runCatching {
                        repositorio.gasolinerasDeProvinciaAhora(gasolinera.provinciaId)
                    }.getOrDefault(emptyList()).mapNotNull { it.precios[carburante] }.let { resumir(it)?.media }
                val historico =
                    try {
                        EstadoHistorico.Datos(repositorio.historico(gasolinera, carburante))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        EstadoHistorico.Error
                    }
                _estado.update { it.copy(historico = historico, mediaProvincia = media) }
            }
    }
}
