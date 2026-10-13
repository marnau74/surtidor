package es.surtidor.ui

import androidx.compose.runtime.Composable
import es.surtidor.dominio.Coordenadas

/**
 * Una función que pide la ubicación (con el permiso, si hace falta) y llama a [alObtener] con el resultado, o nulo
 * si la plataforma no tiene ubicación (escritorio): entonces la interfaz no ofrece «Cerca de mí».
 */
@Composable
expect fun recordarPeticionDeUbicacion(alObtener: (Coordenadas?) -> Unit): (() -> Unit)?
