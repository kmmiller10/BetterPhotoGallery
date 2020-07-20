package me.kmmiller.better.photo.gallery.extensions

import io.realm.Realm
import io.realm.RealmResults
import me.kmmiller.better.photo.gallery.PhotoObject

fun Realm.findAllPhotos(): RealmResults<PhotoObject> = where(PhotoObject::class.java).findAll()