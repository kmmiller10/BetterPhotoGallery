package me.kmmiller.better.photo.gallery.extensions

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kmmiller.better.photo.gallery.*
import java.io.*
import java.util.*
import kotlin.collections.ArrayList

@Suppress("DEPRECATION") // Branched to handle higher versions, but use deprecated on lower verions
fun PhotoObject.createGridItem(contentResolver: ContentResolver, realm: Realm?): PhotoGridFragment.GridItem {
    val thumbnailPhoto: PhotoObject? = realm?.findThumbnail(this)

    val uri = Uri.parse(thumbnailPhoto?.uriString ?: uriString)

    val bitmap = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
         MediaStore.Images.Media.getBitmap(contentResolver, uri)
    } else {
        val imageSource = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(imageSource)
    }

    return PhotoGridFragment.GridItem (
        name = name,
        image = bitmap,
        isDir = false,
        photoId = id
    )
}

fun DirectoryObject.createGridItem(): PhotoGridFragment.GridItem {
    return PhotoGridFragment.GridItem(
        name = name,
        image = null,
        isDir = true,
        dirId = id
    )
}

suspend fun ArrayList<DirectoryObject>.getPhotosFromDirectories(): ArrayList<PhotoObject> {
    val photoObjects = ArrayList<PhotoObject>()
    forEach { dir ->
        val dirPath = dir.path
        File(dirPath).listFiles(PhotoFilter())?.let { photos ->
            photos.forEach { file ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val photo = PhotoObject().apply {
                    id = UUID.randomUUID().toString()
                    width = options.outWidth
                    height = options.outHeight

                    uriString = file.toURI().toString()
                    path = file.canonicalPath
                    parentId = dir.id

                    name = file.name
                    mimeType = file.extension
                    modified = file.lastModified()
                }
                photoObjects.add(photo)
            }
        }
    }
    return withContext(Dispatchers.IO) {
        photoObjects
    }
}

@Suppress("DEPRECATION")
suspend fun getPhotoDirs(): ArrayList<DirectoryObject> {
    val dirs = ArrayList<DirectoryObject>()
    val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val children = dcim.listFiles(DirectoryFilter())
    children?.forEach { dir ->
        val dirObject = DirectoryObject().apply {
            id = UUID.randomUUID().toString()
            name = dir.name
            path = dir.absolutePath
            isThumbnailDir = false
        }
        dirs.add(dirObject)
    }
    return withContext(Dispatchers.IO) {
        dirs
    }
}

@Suppress("DEPRECATION")
suspend fun getThumbnailDirs(): ArrayList<DirectoryObject> {
    val dirs = ArrayList<DirectoryObject>()
    val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val children = dcim.listFiles(ThumbnailDirFilter())
    children?.forEach { dir ->
        val dirObject = DirectoryObject().apply {
            id = UUID.randomUUID().toString()
            name = dir.name
            path = dir.absolutePath
            isThumbnailDir = true
        }
        dirs.add(dirObject)
    }
    return withContext(Dispatchers.IO) {
        dirs
    }
}