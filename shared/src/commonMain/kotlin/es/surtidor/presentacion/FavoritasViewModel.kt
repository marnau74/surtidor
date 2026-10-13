package es.surtidor.presentacion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.surtidor.datos.FavoritaConDatos
import es.surtidor.datos.RepositorioDeGasolineras
import es.surtidor.datos.ServicioDeAvisos
import es.surtidor.dominio.Aviso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoritasViewModel(
    private val repositorio: RepositorioDeGasolineras,
    private val avisos: ServicioDeAvisos,
) : ViewModel() {
    val favoritas: StateFlow<List<FavoritaConDatos>?> =
        repositorio.favoritas().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _avisosNuevos = MutableStateFlow<List<Aviso>>(emptyList())

    /** En escritorio no hay tarea en segundo plano: los avisos se comprueban al abrir y se enseñan aquí. */
    val avisosNuevos: StateFlow<List<Aviso>> = _avisosNuevos.asStateFlow()

    fun comprobarAvisos() {
        viewModelScope.launch { _avisosNuevos.value = runCatching { avisos.comprobar() }.getOrDefault(emptyList()) }
    }

    fun descartarAvisos() {
        _avisosNuevos.value = emptyList()
    }

    fun quitar(gasolineraId: Long) {
        viewModelScope.launch { repositorio.quitarFavorita(gasolineraId) }
    }
}
