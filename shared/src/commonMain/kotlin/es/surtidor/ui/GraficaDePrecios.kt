package es.surtidor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.surtidor.dominio.PrecioDelDia

/** Texto equivalente a la gráfica: lo que lee un lector de pantalla y lo que comprueban los tests. */
fun resumenDeLaGrafica(precios: List<PrecioDelDia>): String {
    if (precios.isEmpty()) return "Sin histórico de precios"
    val primero = precios.first()
    val ultimo = precios.last()
    val minimo = precios.minBy { it.precio }
    val maximo = precios.maxBy { it.precio }
    return "Del ${fechaCorta(primero.fecha)} al ${fechaCorta(ultimo.fecha)}: de ${precioHablado(primero.precio)} a " +
        "${precioHablado(ultimo.precio)}. Mínimo, ${precioHablado(minimo.precio)} el ${fechaCorta(minimo.fecha)}; " +
        "máximo, ${precioHablado(maximo.precio)} el ${fechaCorta(maximo.fecha)}."
}

/**
 * La evolución del precio día a día. Los días sin dato no se inventan: la línea los salta. El eje vertical va del
 * mínimo al máximo con un margen, porque las diferencias son de céntimos y desde cero no se verían.
 */
@Composable
fun GraficaDePrecios(
    precios: List<PrecioDelDia>,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val rejilla = MaterialTheme.colorScheme.outlineVariant
    val resumen = resumenDeLaGrafica(precios)
    Column(modifier.semantics { contentDescription = resumen }) {
        if (precios.size < 2) {
            Text("Aún no hay días suficientes para la gráfica.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val minimo = precios.minOf { it.precio }
        val maximo = precios.maxOf { it.precio }
        val margen = ((maximo - minimo) * 0.15).coerceAtLeast(0.005)
        val abajo = minimo - margen
        val arriba = maximo + margen
        val primerDia = precios.first().fecha.toEpochDays()
        val dias = (precios.last().fecha.toEpochDays() - primerDia).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth()) {
            Text(precio(maximo), style = MaterialTheme.typography.labelSmall.merge(estiloPrecio))
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 6.dp)) {
            val x = { dia: Long -> (dia - primerDia).toFloat() / dias * size.width }
            val y = { valor: Double -> ((arriba - valor) / (arriba - abajo)).toFloat() * size.height }
            listOf(0f, 0.5f, 1f).forEach { f ->
                drawLine(
                    rejilla,
                    Offset(0f, size.height * f),
                    Offset(size.width, size.height * f),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            val trazo = Path()
            var anterior: Long? = null
            precios.forEach { p ->
                val dia = p.fecha.toEpochDays()
                val punto = Offset(x(dia), y(p.precio))
                if (anterior == null ||
                    dia - anterior > 1
                ) {
                    trazo.moveTo(punto.x, punto.y)
                } else {
                    trazo.lineTo(punto.x, punto.y)
                }
                anterior = dia
            }
            drawPath(trazo, color, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val ultimo = precios.last()
            drawCircle(color, radius = 6f, center = Offset(x(ultimo.fecha.toEpochDays()), y(ultimo.precio)))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(precio(minimo), style = MaterialTheme.typography.labelSmall.merge(estiloPrecio))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                "${fechaCorta(precios.first().fecha)} — ${fechaCorta(precios.last().fecha)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
