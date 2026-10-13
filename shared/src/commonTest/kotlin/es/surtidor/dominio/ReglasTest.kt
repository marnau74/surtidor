package es.surtidor.dominio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReglasTest {
    private val murcia = Coordenadas(37.9922, -1.1307)
    private val cartagena = Coordenadas(37.6257, -0.9966)

    private fun gasolinera(
        id: Long,
        precio95: Double?,
        en: Coordenadas?,
        municipio: String = "Murcia",
        rotulo: String = "GASOLINERA $id",
    ) = Gasolinera(
        id = id,
        provinciaId = "30",
        rotulo = rotulo,
        direccion = "Calle $id",
        municipio = municipio,
        localidad = municipio.uppercase(),
        codigoPostal = "30000",
        coordenadas = en,
        horario = "L-D: 24H",
        precios = listOfNotNull(precio95?.let { Carburante.GASOLINA_95 to it }, Carburante.GASOLEO_A to 1.7).toMap(),
    )

    @Test
    fun laDistanciaEntreMurciaYCartagenaEsLaReal() {
        val km = distanciaKm(murcia, cartagena)
        assertTrue(abs(km - 42.4) < 1.0, "Murcia-Cartagena en línea recta: $km km")
        assertEquals(0.0, distanciaKm(murcia, murcia))
    }

    @Test
    fun ordenaPorPrecioYDesempataPorCercania() {
        val lista =
            listOf(
                gasolinera(1, 1.80, cartagena),
                gasolinera(2, 1.75, cartagena),
                gasolinera(3, 1.75, murcia),
                gasolinera(4, null, murcia),
            )
        val ordenadas = ordenar(lista, Carburante.GASOLINA_95, Orden.PRECIO, desde = murcia)
        assertEquals(listOf(3L, 2L, 1L), ordenadas.map { it.gasolinera.id }, "la 4 no vende gasolina 95")
    }

    @Test
    fun ordenaPorCercaniaConLasSinCoordenadasAlFinal() {
        val lista = listOf(gasolinera(1, 1.6, null), gasolinera(2, 1.9, cartagena), gasolinera(3, 1.8, murcia))
        val ordenadas = ordenar(lista, Carburante.GASOLINA_95, Orden.DISTANCIA, desde = murcia)
        assertEquals(listOf(3L, 2L, 1L), ordenadas.map { it.gasolinera.id })
        assertNull(ordenadas.last().distanciaKm)
    }

    @Test
    fun buscaSinImportarMayusculasNiTildes() {
        val lista =
            listOf(gasolinera(1, 1.8, null, municipio = "Águilas"), gasolinera(2, 1.7, null, municipio = "Lorca"))
        assertEquals(
            listOf(1L),
            ordenar(lista, Carburante.GASOLINA_95, Orden.PRECIO, texto = "AGUILAS").map {
                it.gasolinera.id
            },
        )
        assertEquals(2, ordenar(lista, Carburante.GASOLINA_95, Orden.PRECIO, texto = "  ").size)
    }

    @Test
    fun filtraPorRadio() {
        val lista = listOf(gasolinera(1, 1.8, murcia), gasolinera(2, 1.7, cartagena), gasolinera(3, 1.6, null))
        val cerca = ordenar(lista, Carburante.GASOLINA_95, Orden.PRECIO, desde = murcia, radioKm = 10.0)
        assertEquals(listOf(1L), cerca.map { it.gasolinera.id })
    }

    @Test
    fun resumeMinimoMediaYMaximo() {
        val r = resumir(listOf(1.70, 1.80, 1.90))!!
        assertEquals(1.70, r.minimo)
        assertTrue(abs(r.media - 1.80) < 1e-9)
        assertEquals(1.90, r.maximo)
        assertEquals(3, r.gasolineras)
        assertNull(resumir(emptyList()))
    }

    @Test
    fun avisaSoloCuandoBajaDelUmbralYNoRepiteElMismoPrecio() {
        val g = gasolinera(7, 1.70, murcia)
        val favorita = Favorita(7, Carburante.GASOLINA_95, umbral = 1.72)
        val primera = avisosPendientes(listOf(favorita), mapOf(7L to g), emptyMap())
        assertEquals(1, primera.size)
        assertEquals(1.70, primera.single().precio)

        assertTrue(
            avisosPendientes(listOf(favorita), mapOf(7L to g), mapOf(7L to 1.70)).isEmpty(),
            "mismo precio: ya avisado",
        )
        val masBarata = g.copy(precios = mapOf(Carburante.GASOLINA_95 to 1.65))
        assertEquals(
            1,
            avisosPendientes(listOf(favorita), mapOf(7L to masBarata), mapOf(7L to 1.70)).size,
            "ha bajado más",
        )
    }

    @Test
    fun noAvisaSinUmbralNiPorEncimaNiSiNoVendeElCarburante() {
        val g = gasolinera(7, 1.80, murcia)
        assertTrue(
            avisosPendientes(listOf(Favorita(7, Carburante.GASOLINA_95, null)), mapOf(7L to g), emptyMap()).isEmpty(),
        )
        assertTrue(
            avisosPendientes(listOf(Favorita(7, Carburante.GASOLINA_95, 1.75)), mapOf(7L to g), emptyMap()).isEmpty(),
        )
        assertTrue(avisosPendientes(listOf(Favorita(7, Carburante.GLP, 2.0)), mapOf(7L to g), emptyMap()).isEmpty())
        assertTrue(
            avisosPendientes(listOf(Favorita(8, Carburante.GASOLINA_95, 2.0)), mapOf(7L to g), emptyMap()).isEmpty(),
        )
    }

    @Test
    fun soloEsAbierta24HorasSiElHorarioLoDiceParaTodaLaSemana() {
        assertTrue(gasolinera(1, 1.8, null).abierta24Horas)
        assertTrue(gasolinera(1, 1.8, null).copy(horario = " l-d: 24h ").abierta24Horas)
        assertTrue(!gasolinera(1, 1.8, null).copy(horario = "L-S: 24H; D: 08:00-15:00").abierta24Horas)
        assertTrue(!gasolinera(1, 1.8, null).copy(horario = "").abierta24Horas)
    }
}
