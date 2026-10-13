package es.surtidor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import es.surtidor.datos.FavoritaConDatos
import es.surtidor.dominio.Aviso

@Composable
fun PantallaFavoritas(
    favoritas: List<FavoritaConDatos>?,
    avisos: List<Aviso>,
    abrir: (Long) -> Unit,
    quitar: (Long) -> Unit,
    descartarAvisos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Favoritas",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (avisos.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        avisos.forEach { a ->
                            Text(
                                "${a.gasolinera.rotulo}: ${a.carburante.nombre} a ${precio(
                                    a.precio,
                                )} (tu aviso: ${precio(a.umbral)})",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                        TextButton(onClick = descartarAvisos) { Text("Entendido") }
                    }
                }
            }
        }
        when {
            favoritas == null -> {}

            favoritas.isEmpty() -> {
                item {
                    TextoSuave("Aún no tienes favoritas. Ábrelas desde la lista y pulsa «Guardar en favoritas».")
                }
            }

            else -> {
                items(favoritas, key = { it.favorita.gasolineraId }) { f ->
                    val g = f.gasolinera
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = CardDefaults.outlinedCardBorder(),
                        modifier =
                            Modifier.fillMaxWidth().clickable(
                                role = Role.Button,
                            ) { abrir(f.favorita.gasolineraId) },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    g?.rotulo ?: "Gasolinera ${f.favorita.gasolineraId}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                TextoSuave(
                                    listOfNotNull(g?.municipio, f.favorita.carburante.nombre).joinToString(" · "),
                                )
                                f.favorita.umbral?.let { TextoSuave("Aviso por debajo de ${precio(it)}") }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                g?.precios?.get(f.favorita.carburante)?.let {
                                    Text(precio(it), style = MaterialTheme.typography.titleLarge.merge(estiloPrecio))
                                }
                                TextButton(onClick = { quitar(f.favorita.gasolineraId) }) { Text("Quitar") }
                            }
                        }
                    }
                }
            }
        }
    }
}
