package es.surtidor.datos

import es.surtidor.Muestras
import es.surtidor.datos.remoto.RespuestaDto
import es.surtidor.datos.remoto.aDominio
import es.surtidor.datos.remoto.fechaDeLaApi
import es.surtidor.datos.remoto.nombreDeProvincia
import es.surtidor.datos.remoto.numeroEspanol
import es.surtidor.datos.remoto.precioValido
import es.surtidor.dominio.Carburante
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MapeoTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val actual = json.decodeFromString<RespuestaDto>(Muestras.ACTUAL_MURCIA).aDominio()
    private val porId = actual.gasolineras.associateBy { it.id }

    @Test
    fun losNumerosLleganConComaYPuedenVenirVacios() {
        assertEquals(1.889, numeroEspanol("1,889"))
        assertEquals(-1.049806, numeroEspanol("-1,049806"))
        assertEquals(38.0, numeroEspanol(" 38 "))
        assertNull(numeroEspanol(""))
        assertNull(numeroEspanol("   "))
        assertNull(numeroEspanol("n/d"))
    }

    @Test
    fun losTextosSeLimpianDeEspaciosRepetidos() {
        assertEquals(
            "AVENIDA DON JUAN DE BORBON EN MURCIA, SN",
            es.surtidor.datos.remoto
                .limpio(" AVENIDA DON JUAN DE BORBON  EN  MURCIA, SN "),
        )
    }

    @Test
    fun unPrecioAbsurdoSeDescartaEnLugarDeEnsenarse() {
        assertEquals(1.799, precioValido("1,799"))
        assertNull(precioValido("0,000"))
        assertNull(precioValido("189,9"))
    }

    @Test
    fun laFechaDelMinisterioEsHoraPeninsular() {
        // 13 de octubre: horario de verano (UTC+2).
        assertEquals(Instant.parse("2026-10-13T17:45:20Z"), fechaDeLaApi("13/10/2026 19:45:20"))
        assertEquals(Instant.parse("2026-09-30T22:00:00Z"), fechaDeLaApi("01/10/2026 0:00:00"))
        // En enero, UTC+1.
        assertEquals(Instant.parse("2026-01-15T07:30:00Z"), fechaDeLaApi("15/01/2026 8:30:00"))
    }

    @Test
    fun losNombresDeProvinciaSeLeenComoSeEscriben() {
        assertEquals("A Coruña", nombreDeProvincia("CORUÑA (A)"))
        assertEquals("Las Palmas", nombreDeProvincia("PALMAS (LAS)"))
        assertEquals("Illes Balears", nombreDeProvincia("BALEARS (ILLES)"))
        assertEquals("La Rioja", nombreDeProvincia("RIOJA (LA)"))
        assertEquals("Araba/Álava", nombreDeProvincia("ARABA/ÁLAVA"))
        assertEquals("Castellón / Castelló", nombreDeProvincia("CASTELLÓN / CASTELLÓ"))
        assertEquals("Santa Cruz de Tenerife", nombreDeProvincia("SANTA CRUZ DE TENERIFE"))
        assertEquals("Ciudad Real", nombreDeProvincia("CIUDAD REAL"))
    }

    @Test
    fun leeLaRespuestaRealCompleta() {
        assertEquals(Instant.parse("2026-10-13T17:45:20Z"), actual.publicadosEn)
        assertEquals(5, actual.gasolineras.size)
        val montepinar = assertNotNull(porId[4149])
        assertEquals("E.S. MONTEPINAR", montepinar.rotulo)
        assertEquals("Abarán", montepinar.municipio)
        assertEquals("30", montepinar.provinciaId)
        assertEquals(1.909, montepinar.precios[Carburante.GASOLEO_A])
        assertTrue(montepinar.abierta24Horas)
        assertNotNull(montepinar.coordenadas)
    }

    @Test
    fun unaGasolineraSoloTieneLosCarburantesQueVende() {
        val sinGasolina = assertNotNull(porId[13177])
        assertNull(sinGasolina.precios[Carburante.GASOLINA_95])
        assertEquals(1.867, sinGasolina.precios[Carburante.GASOLEO_A])
        val gasNatural = assertNotNull(porId[14578])
        assertNull(gasNatural.precios[Carburante.GASOLEO_A])
        assertNotNull(gasNatural.precios[Carburante.GNC])
        assertNotNull(porId[2162]?.precios?.get(Carburante.GLP))
    }
}
