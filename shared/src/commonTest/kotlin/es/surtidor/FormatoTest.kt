package es.surtidor

import es.surtidor.ui.decimales
import es.surtidor.ui.diferenciaEnCentimos
import es.surtidor.ui.distancia
import es.surtidor.ui.momento
import es.surtidor.ui.precio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class FormatoTest {
    @Test
    fun preciosConTresDecimalesYComa() {
        assertEquals("1,889 €", precio(1.889))
        assertEquals("2,000 €", precio(2.0))
        assertEquals("1,900 €", precio(1.8999))
    }

    @Test
    fun diferenciasEnCentimosConSigno() {
        assertEquals("−3,4 cts", diferenciaEnCentimos(-0.034))
        assertEquals("+2,0 cts", diferenciaEnCentimos(0.02))
        assertEquals("0,0 cts", diferenciaEnCentimos(0.0004))
    }

    @Test
    fun distanciasEnMetrosOKilometros() {
        assertEquals("850 m", distancia(0.85))
        assertEquals("12,4 km", distancia(12.38))
    }

    @Test
    fun decimalesSinDependerDelIdioma() {
        assertEquals("-1,05", decimales(-1.049806, 2))
        assertEquals("0,0", decimales(-0.01, 1))
        assertEquals("3", decimales(2.6, 0))
    }

    @Test
    fun momentosRelativosAHoyEnHoraPeninsular() {
        val ahora = Instant.parse("2026-10-13T18:00:00Z")
        assertEquals("hoy a las 19:45", momento(Instant.parse("2026-10-13T17:45:20Z"), ahora))
        assertEquals("ayer a las 8:05", momento(Instant.parse("2026-10-12T06:05:00Z"), ahora))
        assertEquals("1 oct a las 0:00", momento(Instant.parse("2026-09-30T22:00:00Z"), ahora))
    }
}
