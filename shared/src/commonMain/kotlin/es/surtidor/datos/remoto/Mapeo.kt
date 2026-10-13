package es.surtidor.datos.remoto

import es.surtidor.dominio.Carburante
import es.surtidor.dominio.Coordenadas
import es.surtidor.dominio.Gasolinera
import es.surtidor.dominio.PreciosPublicados
import es.surtidor.dominio.Provincia
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** Hora oficial de los datos: la de la península. */
val ZONA_ESPANA: TimeZone = TimeZone.of("Europe/Madrid")

/**
 * Un número de la API («1,889», «38,175778», «-1,049806») o nulo si viene vacío o no es un número. Nunca lanza:
 * un dato malo deja sin ese precio a una gasolinera, no la lista entera.
 */
internal fun numeroEspanol(texto: String): Double? {
    val limpio = texto.trim().replace(',', '.')
    if (limpio.isEmpty()) return null
    return limpio.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** Un precio razonable en euros: descarta ceros y valores absurdos que a veces publica alguna gasolinera. */
internal fun precioValido(texto: String): Double? = numeroEspanol(texto)?.takeIf { it > 0.2 && it < 10.0 }

/** «13/10/2026 19:45:20» o «01/10/2026 0:00:00», en hora peninsular. */
internal fun fechaDeLaApi(texto: String): kotlin.time.Instant {
    val (dia, hora) = texto.trim().split(" ").let { it[0] to it.getOrElse(1) { "0:00:00" } }
    val (d, m, a) = dia.split("/").map { it.toInt() }
    val (h, min, s) = hora.split(":").map { it.toInt() }
    return LocalDateTime(a, m, d, h, min, s).toInstant(ZONA_ESPANA)
}

/** Sin espacios al principio ni al final, y sin espacios repetidos (la fuente trae «BORBON  EN  MURCIA»). */
internal fun limpio(texto: String): String = texto.trim().replace(Regex("""\s+"""), " ")

internal fun EstacionDto.aDominio(): Gasolinera? {
    val id = id.trim().toLongOrNull() ?: return null
    val lat = numeroEspanol(latitud)
    val lon = numeroEspanol(longitud)
    val coordenadas =
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
            Coordenadas(lat, lon)
        } else {
            null
        }
    val precios =
        buildMap {
            precioValido(gasolina95)?.let { put(Carburante.GASOLINA_95, it) }
            precioValido(gasolina98)?.let { put(Carburante.GASOLINA_98, it) }
            precioValido(gasoleoA)?.let { put(Carburante.GASOLEO_A, it) }
            precioValido(gasoleoPremium)?.let { put(Carburante.GASOLEO_PREMIUM, it) }
            precioValido(glp)?.let { put(Carburante.GLP, it) }
            precioValido(gnc)?.let { put(Carburante.GNC, it) }
        }
    return Gasolinera(
        id = id,
        provinciaId = provinciaId.trim(),
        rotulo = limpio(rotulo).ifEmpty { "Sin rótulo" },
        direccion = limpio(direccion),
        municipio = limpio(municipio),
        localidad = limpio(localidad),
        codigoPostal = limpio(codigoPostal),
        coordenadas = coordenadas,
        horario = limpio(horario),
        precios = precios,
    )
}

internal fun RespuestaDto.aDominio(): PreciosPublicados =
    PreciosPublicados(
        publicadosEn = fechaDeLaApi(fecha),
        gasolineras = estaciones.mapNotNull { it.aDominio() },
    )

internal fun ProvinciaDto.aDominio(): Provincia = Provincia(id.trim(), nombreDeProvincia(nombre))

private val PALABRAS_MENORES = setOf("de", "del", "la", "las", "el", "los", "y")

/**
 * Los nombres llegan en mayúsculas y con el artículo al final: «CORUÑA (A)» pasa a «A Coruña», «PALMAS (LAS)» a
 * «Las Palmas» y «CASTELLÓN / CASTELLÓ» a «Castellón / Castelló».
 */
fun nombreDeProvincia(texto: String): String {
    val sinParentesis =
        Regex(
            """^(.*?)\s*\((.+)\)\s*$""",
        ).matchEntire(texto.trim())?.let { "${it.groupValues[2]} ${it.groupValues[1]}" }
            ?: texto.trim()
    return sinParentesis
        .lowercase()
        .split(" ")
        .mapIndexed { i, palabra ->
            if (i > 0 && palabra in PALABRAS_MENORES) {
                palabra
            } else {
                palabra.split("/").joinToString("/") { parte -> parte.replaceFirstChar { it.uppercase() } }
            }
        }.joinToString(" ")
}
