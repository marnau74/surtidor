package es.surtidor.datos

import es.surtidor.dominio.Aviso
import es.surtidor.dominio.Favorita
import es.surtidor.dominio.avisosPendientes
import kotlinx.coroutines.flow.first

/**
 * Comprueba los precios de las favoritas. Se llama desde la tarea periódica de Android y al abrir la app de
 * escritorio; la notificación la pone cada plataforma.
 */
class ServicioDeAvisos(
    private val repositorio: RepositorioDeGasolineras,
) {
    /**
     * Refresca las provincias de las favoritas que tienen umbral y devuelve los avisos nuevos, ya anotados para no
     * repetirlos. Cuando una favorita vuelve a superar su umbral se olvida el aviso: si baja otra vez, se avisa.
     * Una provincia que no se puede refrescar se comprueba con lo último guardado.
     */
    suspend fun comprobar(): List<Aviso> {
        val favoritas: List<Favorita> =
            repositorio
                .favoritas()
                .first()
                .map { it.favorita }
                .filter { it.umbral != null }
        if (favoritas.isEmpty()) return emptyList()
        val actuales = repositorio.gasolinerasConUltimoPrecio(favoritas.map { it.gasolineraId })
        actuales.values.map { it.provinciaId }.toSet().forEach { provincia ->
            runCatching { repositorio.refrescar(provincia) }
        }
        val gasolineras = repositorio.gasolinerasConUltimoPrecio(favoritas.map { it.gasolineraId })
        val avisos = avisosPendientes(favoritas, gasolineras, repositorio.ultimosPreciosAvisados())
        avisos.forEach { repositorio.anotarAviso(it.gasolinera.id, it.precio) }
        favoritas.forEach { favorita ->
            val precio = gasolineras[favorita.gasolineraId]?.precios?.get(favorita.carburante)
            val umbral = favorita.umbral
            if (precio != null && umbral != null &&
                precio > umbral
            ) {
                repositorio.anotarAviso(favorita.gasolineraId, null)
            }
        }
        return avisos
    }
}
