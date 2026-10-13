package es.surtidor.ui

import es.surtidor.datos.remoto.ZONA_ESPANA
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")

/** «1,889 €»: tres decimales, como los publica el Ministerio. */
fun precio(euros: Double): String = "${decimales(euros, 3)} €"

/** Para lectores de pantalla: «1 euro con 889 milésimas» se entiende peor que «1,889 euros». */
fun precioHablado(euros: Double): String = "${decimales(euros, 3)} euros"

/** «+3,2 cts» / «−5,0 cts»: diferencia en céntimos con una cifra decimal. */
fun diferenciaEnCentimos(euros: Double): String {
    val centimos = euros * 100
    val signo =
        when {
            centimos > 0.05 -> "+"
            centimos < -0.05 -> "−"
            else -> ""
        }
    return "$signo${decimales(abs(centimos), 1)} cts"
}

/** «850 m» o «12,4 km». */
fun distancia(km: Double): String = if (km < 1) "${(km * 1000).roundToLong()} m" else "${decimales(km, 1)} km"

fun fechaCorta(fecha: LocalDate): String = "${fecha.day} ${MESES[fecha.month.ordinal]}"

/** «hoy a las 19:45», «ayer a las 8:05» o «13 oct a las 19:45», en hora peninsular. */
fun momento(
    instante: Instant,
    ahora: Instant,
): String {
    val local = instante.toLocalDateTime(ZONA_ESPANA)
    val hoy = ahora.toLocalDateTime(ZONA_ESPANA).date
    val dia =
        when (local.date) {
            hoy -> "hoy"
            hoy.minusDays(1) -> "ayer"
            else -> fechaCorta(local.date)
        }
    return "$dia a las ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}

private fun LocalDate.minusDays(dias: Int): LocalDate = LocalDate.fromEpochDays(toEpochDays() - dias)

/** Número con coma decimal y un número fijo de decimales, sin depender del locale de la plataforma. */
fun decimales(
    valor: Double,
    cifras: Int,
): String {
    var factor = 1L
    repeat(cifras) { factor *= 10 }
    val redondeado = (abs(valor) * factor).roundToLong()
    val entera = redondeado / factor
    val fraccion = (redondeado % factor).toString().padStart(cifras, '0')
    val signo = if (valor < 0 && redondeado != 0L) "-" else ""
    return if (cifras == 0) "$signo$entera" else "$signo$entera,$fraccion"
}
