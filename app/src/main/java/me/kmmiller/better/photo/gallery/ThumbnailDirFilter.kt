package me.kmmiller.better.photo.gallery

import java.io.File
import java.io.FileFilter

class ThumbnailDirFilter: FileFilter {
    override fun accept(file: File): Boolean = file.isDirectory && file.name.startsWith(".thumbnail")
}