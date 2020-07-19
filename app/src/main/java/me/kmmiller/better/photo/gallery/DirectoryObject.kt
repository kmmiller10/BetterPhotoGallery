package me.kmmiller.better.photo.gallery

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

@RealmClass
open class DirectoryObject: RealmObject() {
    @PrimaryKey
    var id: Long = 0

    var name: String = ""
}