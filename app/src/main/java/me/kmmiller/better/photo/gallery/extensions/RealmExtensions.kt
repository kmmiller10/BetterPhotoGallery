package me.kmmiller.better.photo.gallery.extensions

import io.realm.Realm
import io.realm.RealmResults
import me.kmmiller.better.photo.gallery.DirectoryObject
import me.kmmiller.better.photo.gallery.PhotoObject

fun Realm.findAllPhotos(): RealmResults<PhotoObject> = where(PhotoObject::class.java).findAll()

fun Realm.deletePhotosFromRealm() {
    where(PhotoObject::class.java).findAll().deleteAllFromRealm()
}

fun Realm.findAllDirectories(): RealmResults<DirectoryObject> = where(DirectoryObject::class.java).findAll()

fun Realm.deleteDirsFromRealm() {
    where(DirectoryObject::class.java).findAll().deleteAllFromRealm()
}

fun Realm.findThumbnail(photo: PhotoObject): PhotoObject? {
    return where(PhotoObject::class.java)
        .equalTo("isThumbnail", true)
        .and()
        .equalTo("name", photo.name)
        .findFirst()
}