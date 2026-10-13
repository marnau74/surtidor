package es.surtidor.dominio

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val RADIO_TIERRA_KM = 6371.0088

private fun radianes(grados: Double) = grados * PI / 180

/** Distancia en línea recta (fórmula del semiverseno), en kilómetros. */
fun distanciaKm(
    a: Coordenadas,
    b: Coordenadas,
): Double {
    val lat1 = radianes(a.latitud)
    val lat2 = radianes(b.latitud)
    val dLat = lat2 - lat1
    val dLon = radianes(b.longitud - a.longitud)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * RADIO_TIERRA_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

enum class Orden { PRECIO, DISTANCIA }

/** Una gasolinera en una lista: con el precio del carburante elegido y, si se sabe, la distancia. */
data class GasolineraEnLista(
    val gasolinera: Gasolinera,
    val precio: Double,
    val distanciaKm: Double?,
)

/**
 * Las gasolineras que venden el carburante, filtradas por texto (rótulo, municipio, localidad o dirección, sin
 * importar mayúsculas ni tildes) y ordenadas. A igual precio, la más cercana; a igual distancia, la más barata.
 */
fun ordenar(
    gasolineras: List<Gasolinera>,
    carburante: Carburante,
    orden: Orden,
    desde: Coordenadas? = null,
    texto: String = "",
    radioKm: Double? = null,
): List<GasolineraEnLista> {
    val buscado = normalizar(texto)
    val candidatas =
        gasolineras
            .mapNotNull { g ->
                val precio = g.precios[carburante] ?: return@mapNotNull null
                val distancia = if (desde != null && g.coordenadas != null) distanciaKm(desde, g.coordenadas) else null
                GasolineraEnLista(g, precio, distancia)
            }.filter { buscado.isEmpty() || coincide(it.gasolinera, buscado) }
            .filter { radioKm == null || (it.distanciaKm != null && it.distanciaKm <= radioKm) }
    val porDistancia = compareBy<GasolineraEnLista, Double?>(nullsLast()) { it.distanciaKm }
    val porPrecio = compareBy<GasolineraEnLista> { it.precio }
    val comparador =
        when (orden) {
            Orden.PRECIO -> porPrecio.then(porDistancia)
            Orden.DISTANCIA -> porDistancia.then(porPrecio)
        }.thenBy { it.gasolinera.id }
    return candidatas.sortedWith(comparador)
}

private fun coincide(
    g: Gasolinera,
    buscado: String,
): Boolean =
    listOf(g.rotulo, g.municipio, g.localidad, g.direccion, g.codigoPostal)
        .any { normalizar(it).contains(buscado) }

/** Minúsculas y sin tildes ni diéresis, para buscar «aguilas» y encontrar «Águilas». */
fun normalizar(texto: String): String =
    texto
        .trim()
        .lowercase()
        .map {
            when (it) {
                'á', 'à', 'ä', 'â' -> 'a'
                'é', 'è', 'ë', 'ê' -> 'e'
                'í', 'ì', 'ï', 'î' -> 'i'
                'ó', 'ò', 'ö', 'ô' -> 'o'
                'ú', 'ù', 'ü', 'û' -> 'u'
                else -> it
            }
        }.joinToString("")

/** Lo más barato, la media y lo más caro de un carburante en una lista. */
data class Resumen(
    val minimo: Double,
    val media: Double,
    val maximo: Double,
    val gasolineras: Int,
)

fun resumir(precios: List<Double>): Resumen? =
    if (precios.isEmpty()) {
        null
    } else {
        Resumen(precios.min(), precios.average(), precios.max(), precios.size)
    }

/** Un aviso que hay que dar: una favorita ha bajado de su umbral. */
data class Aviso(
    val gasolinera: Gasolinera,
    val carburante: Carburante,
    val precio: Double,
    val umbral: Double,
)

/**
 * Las favoritas cuyo precio actual está en su umbral o por debajo. Si una gasolinera ya se avisó con ese mismo
 * precio, no se vuelve a avisar (se avisa otra vez solo si baja más o si sube y vuelve a bajar).
 */
fun avisosPendientes(
    favoritas: List<Favorita>,
    gasolineras: Map<Long, Gasolinera>,
    ultimoPrecioAvisado: Map<Long, Double>,
): List<Aviso> =
    favoritas.mapNotNull { favorita ->
        val umbral = favorita.umbral ?: return@mapNotNull null
        val gasolinera = gasolineras[favorita.gasolineraId] ?: return@mapNotNull null
        val precio = gasolinera.precios[favorita.carburante] ?: return@mapNotNull null
        val yaAvisado = ultimoPrecioAvisado[favorita.gasolineraId]
        if (precio <= umbral && (yaAvisado == null || precio < yaAvisado)) {
            Aviso(gasolinera, favorita.carburante, precio, umbral)
        } else {
            null
        }
    }
