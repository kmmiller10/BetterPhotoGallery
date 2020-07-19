package me.kmmiller.better.photo.gallery

import android.app.Application
import androidx.multidex.MultiDex
import io.realm.Realm
import io.realm.RealmConfiguration

class BetterGalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        MultiDex.install(this)
        Realm.init(this)
        val realmConfig = RealmConfiguration.Builder().deleteRealmIfMigrationNeeded().build()
        Realm.setDefaultConfiguration(realmConfig)
    }
}