package io.github.munhozmib.cifrario

import android.app.Application
import android.content.Context
import io.github.munhozmib.cifrario.data.AppDatabase
import io.github.munhozmib.cifrario.data.Repository
import io.github.munhozmib.cifrario.data.SettingsRepository
import io.github.munhozmib.cifrario.importer.CifraClubSearch

class AppContainer(context: Context, db: AppDatabase = AppDatabase.build(context)) {
    val repository = Repository(db, java.io.File(context.filesDir, "paginas"))
    val settings = SettingsRepository(context)
    val cifraClubSearch = CifraClubSearch()
}

class CifrarioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as CifrarioApp).container
