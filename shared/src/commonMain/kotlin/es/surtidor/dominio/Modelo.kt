package es.surtidor.dominio

import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/** Los carburantes que enseña la app, con el nombre del campo de precio en la API del Ministerio. */
enum class Carburante(
    val nombre: String,
    val corto: String,
    val campoApi: String,
) {
    GASOLINA_95("Gasolina 95", "95", "Precio Gasolina 95 E5"),
    GASOLINA_98("Gasolina 98", "98", "Precio Gasolina 98 E5"),
    GASOLEO_A("Gasóleo A", "Diésel", "Precio Gasoleo A"),
    GASOLEO_PREMIUM("Gasóleo Premium", "Diésel+", "Precio Gasoleo Premium"),
    GLP("Autogás (GLP)", "GLP", "Precio Gases licuados del petróleo"),
    GNC("Gas natural (GNC)", "GNC", "Precio Gas Natural Comprimido"),
}

data class Provincia(
    val id: String,
    val nombre: String,
)

data class Coordenadas(
    val latitud: Double,
    val longitud: Double,
)

/**
 * Una gasolinera con sus precios del último dato publicado.
 *
 * @property precios euros por litro (o por kg en el GNC); solo los carburantes que vende
 */
data class Gasolinera(
    val id: Long,
    val provinciaId: String,
    val rotulo: String,
    val direccion: String,
    val municipio: String,
    val localidad: String,
    val codigoPostal: String,
    val coordenadas: Coordenadas?,
    val horario: String,
    val precios: Map<Carburante, Double>,
) {
    val abierta24Horas: Boolean get() = horario.trim().uppercase() == "L-D: 24H"
}

data class PrecioDelDia(
    val fecha: LocalDate,
    val precio: Double,
)

/** Los precios de una provincia tal como los publica el Ministerio en un momento dado. */
data class PreciosPublicados(
    val publicadosEn: Instant,
    val gasolineras: List<Gasolinera>,
)

data class Favorita(
    val gasolineraId: Long,
    val carburante: Carburante,
    val umbral: Double?,
)
