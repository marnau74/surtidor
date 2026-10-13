package es.surtidor.datos.remoto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respuesta del Geoportal de carburantes tal cual llega. Las claves llevan tildes, espacios y símbolos, y los números
 * son texto con coma decimal (vacío si no hay dato): por eso todo es `String` y se sanea al mapear.
 */
@Serializable
internal data class RespuestaDto(
    @SerialName("Fecha") val fecha: String,
    @SerialName("ListaEESSPrecio") val estaciones: List<EstacionDto> = emptyList(),
    @SerialName("ResultadoConsulta") val resultado: String = "",
)

@Serializable
internal data class EstacionDto(
    @SerialName("IDEESS") val id: String,
    @SerialName("IDProvincia") val provinciaId: String,
    @SerialName("Rótulo") val rotulo: String = "",
    @SerialName("Dirección") val direccion: String = "",
    @SerialName("Municipio") val municipio: String = "",
    @SerialName("Localidad") val localidad: String = "",
    @SerialName("C.P.") val codigoPostal: String = "",
    @SerialName("Latitud") val latitud: String = "",
    @SerialName("Longitud (WGS84)") val longitud: String = "",
    @SerialName("Horario") val horario: String = "",
    @SerialName("Precio Gasolina 95 E5") val gasolina95: String = "",
    @SerialName("Precio Gasolina 98 E5") val gasolina98: String = "",
    @SerialName("Precio Gasoleo A") val gasoleoA: String = "",
    @SerialName("Precio Gasoleo Premium") val gasoleoPremium: String = "",
    @SerialName("Precio Gases licuados del petróleo") val glp: String = "",
    @SerialName("Precio Gas Natural Comprimido") val gnc: String = "",
)

@Serializable
internal data class ProvinciaDto(
    // Sic: la API escribe «IDPovincia».
    @SerialName("IDPovincia") val id: String,
    @SerialName("Provincia") val nombre: String,
)
