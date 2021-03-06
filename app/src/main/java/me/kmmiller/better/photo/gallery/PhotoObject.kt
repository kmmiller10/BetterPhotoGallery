package me.kmmiller.better.photo.gallery

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

@RealmClass
open class PhotoObject : RealmObject() {
    @PrimaryKey
    var id: String = ""

    var width: Int = 0
    var height: Int = 0

    var uriString: String = ""
    var path: String = ""
    var parentId: String = ""

    var name: String = ""
    var mimeType: String = ""
    var modified: Long = 0
    var isThumbnail: Boolean = false
}