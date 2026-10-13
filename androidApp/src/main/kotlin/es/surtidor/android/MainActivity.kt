package es.surtidor.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import es.surtidor.App

class MainActivity : ComponentActivity() {
    private val permisoDeNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android 13 o superior: las notificaciones necesitan permiso. Se pide una vez; si se niega, la app funciona
        // igual y los avisos se ven en la pantalla de favoritas.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            permisoDeNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { App() }
    }
}
