package me.kmmiller.better.photo.gallery

import java.io.File
import java.io.FileFilter

class PhotoFilter: FileFilter {
    override fun accept(file: File): Boolean = file.extension == "jpg" || file.extension == "png"
}