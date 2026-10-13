package es.surtidor.datos

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import es.surtidor.datos.local.BaseDeDatos
import es.surtidor.datos.remoto.ClienteGeoportal
import es.surtidor.datos.remoto.ZONA_ESPANA
import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Coordenadas
import es.surtidor.dominio.Favorita
import es.surtidor.dominio.Gasolinera
import es.surtidor.dominio.PrecioDelDia
import es.surtidor.dominio.PreciosPublicados
import es.surtidor.dominio.Provincia
import es.surtidor.dominio.normalizar
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import es.surtidor.datos.local.Gasolinera as FilaGasolinera
import es.surtidor.datos.local.Precio as FilaPrecio

/** Lo guardado de una provincia: cuándo publicó el Ministerio esos precios y las gasolineras. */
data class DatosDeProvincia(
    val publicadosEn: Instant,
    val gasolineras: List<Gasolinera>,
)

/** Una favorita con su gasolinera y su precio actual (si aún se conoce). */
data class FavoritaConDatos(
    val favorita: Favorita,
    val gasolinera: Gasolinera?,
)

/**
 * Fuente única de los datos de la app. La interfaz lee siempre de la base local (que avisa de cada cambio con un
 * `Flow`) y la red solo la refresca: sin conexión se sigue viendo lo último descargado, con su fecha.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RepositorioDeGasolineras(
    private val cliente: ClienteGeoportal,
    db: BaseDeDatos,
    private val reloj: Clock,
    private val io: CoroutineDispatcher,
    private val vigencia: Duration = 30.minutes,
) {
    private val q = db.esquemaQueries

    fun datosDeProvincia(provinciaId: String): Flow<DatosDeProvincia?> =
        q
            .ultimaDescarga(provinciaId)
            .asFlow()
            .mapToOneOrNull(io)
            .mapLatest { descarga ->
                if (descarga == null) return@mapLatest null
                withContext(io) {
                    val precios = q.preciosDelDia(provinciaId, descarga.fecha).executeAsList()
                    val gasolineras = montar(q.gasolinerasDeProvincia(provinciaId).executeAsList(), precios)
                    DatosDeProvincia(Instant.fromEpochMilliseconds(descarga.publicado_en), gasolineras)
                }
            }

    /**
     * Descarga los precios actuales de la provincia si lo guardado tiene más de 30 minutos (o siempre, si se fuerza).
     * Lanza [es.surtidor.datos.remoto.ErrorDeRed] si no hay conexión; lo guardado no se toca.
     */
    suspend fun refrescar(
        provinciaId: String,
        forzar: Boolean = false,
    ) {
        if (!forzar) {
            val ultima = withContext(io) { q.ultimaDescarga(provinciaId).executeAsOneOrNull() }
            val ahora = reloj.now()
            if (ultima != null && ahora - Instant.fromEpochMilliseconds(ultima.descargado_en) < vigencia) return
        }
        val publicados = cliente.preciosActuales(provinciaId)
        withContext(io) {
            guardar(provinciaId, publicados, actualizarGasolineras = true)
            // El histórico propio se guarda dos meses: lo que enseña la app son 30 días.
            q.borrarPreciosAntiguos(hoy().minus(DatePeriod(days = 60)).toString())
        }
    }

    /**
     * La evolución de un precio en los últimos [dias] días. Los días que no estén guardados se piden al Ministerio
     * (de cuatro en cuatro) y se guardan para toda la provincia: abrir otra gasolinera de la misma provincia ya no
     * descarga nada. Un día que falle se omite, no estropea el resto.
     */
    suspend fun historico(
        gasolinera: Gasolinera,
        carburante: Carburante,
        dias: Int = 30,
    ): List<PrecioDelDia> {
        val hoy = hoy()
        val desde = hoy.minus(DatePeriod(days = dias - 1))
        val guardados =
            withContext(io) { q.diasDescargados(gasolinera.provinciaId, desde.toString()).executeAsList().toSet() }
        val faltan = (1 until dias).map { hoy.minus(DatePeriod(days = it)) }.filter { it.toString() !in guardados }
        val limite = Semaphore(4)
        coroutineScope {
            faltan
                .map { dia ->
                    async {
                        limite.withPermit {
                            runCatching { cliente.preciosDelDia(gasolinera.provinciaId, dia) }.getOrNull()?.let {
                                dia to
                                    it
                            }
                        }
                    }
                }.awaitAll()
                .filterNotNull()
                .forEach { (dia, publicados) ->
                    withContext(
                        io,
                    ) { guardar(gasolinera.provinciaId, publicados, actualizarGasolineras = false, dia = dia) }
                }
        }
        return withContext(io) {
            q
                .historico(gasolinera.id, carburante.name, desde.toString())
                .executeAsList()
                .map { PrecioDelDia(LocalDate.parse(it.fecha), it.precio) }
        }
    }

    /** Las gasolineras de la provincia con los precios guardados ahora mismo. */
    suspend fun gasolinerasDeProvinciaAhora(provinciaId: String): List<Gasolinera> =
        datosDeProvincia(provinciaId).first()?.gasolineras.orEmpty()

    fun ultimaRevision(provinciaId: String): Flow<Instant?> =
        q
            .ultimaDescarga(provinciaId)
            .asFlow()
            .mapToOneOrNull(io)
            .map { it?.let { d -> Instant.fromEpochMilliseconds(d.descargado_en) } }

    /** Las provincias, de la base o (la primera vez) del Ministerio, ordenadas por nombre. */
    suspend fun provincias(): List<Provincia> {
        val guardadas = withContext(io) { q.provincias().executeAsList() }
        val lista =
            if (guardadas.isNotEmpty()) {
                guardadas.map { Provincia(it.id, it.nombre) }
            } else {
                cliente.provincias().also { nuevas ->
                    withContext(io) { q.transaction { nuevas.forEach { q.guardarProvincia(it.id, it.nombre) } } }
                }
            }
        return lista.sortedBy { normalizar(it.nombre) }
    }

    fun favoritas(): Flow<List<FavoritaConDatos>> =
        q.favoritas().asFlow().mapToList(io).combine(cambiosDePrecios()) { filas, _ ->
            val ids = filas.map { it.gasolinera_id }
            val gasolineras = withContext(io) { gasolinerasConUltimoPrecio(ids) }
            filas.map { fila ->
                FavoritaConDatos(
                    Favorita(fila.gasolinera_id, Carburante.valueOf(fila.carburante), fila.umbral),
                    gasolineras[fila.gasolinera_id],
                )
            }
        }

    suspend fun guardarFavorita(favorita: Favorita) =
        withContext(io) {
            q.guardarFavorita(favorita.gasolineraId, favorita.carburante.name, favorita.umbral, null)
        }

    suspend fun quitarFavorita(gasolineraId: Long) = withContext(io) { q.borrarFavorita(gasolineraId) }

    fun esFavorita(gasolineraId: Long): Flow<Favorita?> =
        q.favoritas().asFlow().mapToList(io).map { filas ->
            filas.firstOrNull { it.gasolinera_id == gasolineraId }?.let {
                Favorita(it.gasolinera_id, Carburante.valueOf(it.carburante), it.umbral)
            }
        }

    /** Las gasolineras con su último precio conocido, estén en la provincia que estén. */
    suspend fun gasolinerasConUltimoPrecio(ids: Collection<Long>): Map<Long, Gasolinera> =
        withContext(io) {
            if (ids.isEmpty()) return@withContext emptyMap()
            val filas = q.gasolinerasPorId(ids).executeAsList()
            val precios = q.ultimosPreciosDe(ids).executeAsList()
            montar(filas, precios).associateBy { it.id }
        }

    suspend fun ultimosPreciosAvisados(): Map<Long, Double> =
        withContext(io) {
            q
                .favoritas()
                .executeAsList()
                .mapNotNull { f ->
                    f.ultimo_precio_avisado?.let { f.gasolinera_id to it }
                }.toMap()
        }

    suspend fun anotarAviso(
        gasolineraId: Long,
        precio: Double?,
    ) = withContext(io) { q.anotarAviso(precio, gasolineraId) }

    suspend fun leerAjuste(clave: String): String? = withContext(io) { q.leerAjuste(clave).executeAsOneOrNull() }

    suspend fun guardarAjuste(
        clave: String,
        valor: String,
    ) = withContext(io) { q.guardarAjuste(clave, valor) }

    private fun cambiosDePrecios(): Flow<Unit> = q.ultimaDescargaGlobal().asFlow().map { }

    private fun hoy(): LocalDate = reloj.now().toLocalDateTime(ZONA_ESPANA).date

    private fun guardar(
        provinciaId: String,
        publicados: PreciosPublicados,
        actualizarGasolineras: Boolean,
        dia: LocalDate = publicados.publicadosEn.toLocalDateTime(ZONA_ESPANA).date,
    ) {
        val fecha = dia.toString()
        q.transaction {
            val conocidas =
                q
                    .gasolinerasDeProvincia(provinciaId)
                    .executeAsList()
                    .map { it.id }
                    .toSet()
            publicados.gasolineras.forEach { g ->
                if (actualizarGasolineras || g.id !in conocidas) {
                    q.guardarGasolinera(
                        g.id,
                        g.provinciaId,
                        g.rotulo,
                        g.direccion,
                        g.municipio,
                        g.localidad,
                        g.codigoPostal,
                        g.coordenadas?.latitud,
                        g.coordenadas?.longitud,
                        g.horario,
                    )
                }
            }
            q.borrarPreciosDelDia(fecha, provinciaId)
            publicados.gasolineras.forEach { g ->
                g.precios.forEach { (carburante, precio) -> q.guardarPrecio(g.id, carburante.name, fecha, precio) }
            }
            q.guardarDescarga(
                provinciaId,
                fecha,
                publicados.publicadosEn.toEpochMilliseconds(),
                reloj.now().toEpochMilliseconds(),
            )
        }
    }

    private fun montar(
        filas: List<FilaGasolinera>,
        precios: List<FilaPrecio>,
    ): List<Gasolinera> {
        val porGasolinera = precios.groupBy { it.gasolinera_id }
        return filas.map { f ->
            Gasolinera(
                id = f.id,
                provinciaId = f.provincia_id,
                rotulo = f.rotulo,
                direccion = f.direccion,
                municipio = f.municipio,
                localidad = f.localidad,
                codigoPostal = f.codigo_postal,
                coordenadas = if (f.latitud != null && f.longitud != null) Coordenadas(f.latitud, f.longitud) else null,
                horario = f.horario,
                precios =
                    porGasolinera[f.id]
                        .orEmpty()
                        .mapNotNull { p ->
                            runCatching { Carburante.valueOf(p.carburante) }.getOrNull()?.let {
                                it to
                                    p.precio
                            }
                        }.toMap(),
            )
        }
    }
}
