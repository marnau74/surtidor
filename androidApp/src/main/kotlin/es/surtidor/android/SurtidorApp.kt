package es.surtidor.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import es.surtidor.moduloComun
import es.surtidor.moduloDePlataforma
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class SurtidorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SurtidorApp)
            modules(moduloComun, moduloDePlataforma)
        }
        crearCanalDeAvisos()
        programarComprobacion()
    }

    private fun crearCanalDeAvisos() {
        val canal =
            NotificationChannel(CANAL_AVISOS, "Avisos de precio", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando una gasolinera favorita baja del precio que has fijado"
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    /** Cada seis horas, con conexión. Si ya está programada, se deja como está (no se reinicia el reloj). */
    private fun programarComprobacion() {
        val peticion =
            PeriodicWorkRequestBuilder<TareaDeAvisos>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork("avisos-de-precio", ExistingPeriodicWorkPolicy.KEEP, peticion)
    }

    companion object {
        const val CANAL_AVISOS = "avisos"
    }
}
