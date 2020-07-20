package me.kmmiller.better.photo.gallery

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

@RealmClass
open class DirectoryObject: RealmObject() {
    @PrimaryKey
    var id: String = ""

    var name: String = ""
    var relativePath: String = ""
    var childPhotoIds: RealmList<Long> = RealmList()
}