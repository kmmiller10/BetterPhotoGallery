package me.kmmiller.better.photo.gallery.extensions

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import io.realm.Realm
import io.realm.RealmList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.kmmiller.better.photo.gallery.DirectoryObject
import me.kmmiller.better.photo.gallery.DirectoryFilter
import me.kmmiller.better.photo.gallery.PhotoGridFragment
import me.kmmiller.better.photo.gallery.PhotoObject
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

@Suppress("DEPRECATION") // Branched to handle higher versions, but use deprecated on lower verions
fun PhotoObject.createGridItem(contentResolver: ContentResolver): PhotoGridFragment.GridItem {
    val uri = Uri.parse(uriString)

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

/**
 * Accesses media store and pulls in photos.
 * Content resolver info: https://developer.android.com/training/data-storage/shared/media
 * Must run on worker thread, see coroutine usage: https://developer.android.com/kotlin/coroutines
 */
@Suppress("DEPRECATION")
suspend fun ContentResolver.getMediaStorePhotos(): ArrayList<PhotoObject> {
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.MIME_TYPE,
        MediaStore.Images.Media.DATE_ADDED,
        MediaStore.Images.Media.DATE_MODIFIED,
        MediaStore.Images.Media.DEFAULT_SORT_ORDER
    )

    /*if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        projection.plusElement(MediaStore.Images.Media.DATA)
    } else {
        projection.plusElement(MediaStore.Images.Media.RELATIVE_PATH)
    }*/

    val selection = "" //""${MediaStore.Images.Media.MIME_TYPE} == ? "
    val selectionArgs = arrayOf<String>()

    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

    val query = query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )

    val photos = ArrayList<PhotoObject>()
    query?.use { cursor ->
        // Cache column indices
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
        val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
        /*val pathColumn = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        } else {
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        }*/

        while(cursor.moveToNext()) {
            val cId = cursor.getLong(idColumn)
            val contentUri: Uri = ContentUris.withAppendedId(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                cId
            )

            val photoObject = PhotoObject().apply {
                id = cId
                width = cursor.getInt(widthColumn)
                height = cursor.getInt(heightColumn)
                uriString = contentUri.toString()
                name = cursor.getString(nameColumn)
                mimeType = cursor.getString(mimeTypeColumn)
                added = cursor.getString(addedColumn)
                modified = cursor.getString(modifiedColumn)
                //path = cursor.getString(pathColumn)
            }

            photos.add(photoObject)
        }
    }

    return withContext(Dispatchers.IO) {
        photos
    }
}

/**
 * Gets the directories of photo files
 * Requires API 29+
 * Projection found here - https://stackoverflow.com/a/36736468
 */
@RequiresApi(api=29)
private suspend fun ContentResolver.getMediaStoreDirectories(): ArrayList<DirectoryObject> {
    val projection = arrayOf(
        "DISTINCT " + MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Images.Media.BUCKET_ID
    )

    val selection = "" //""${MediaStore.Images.Media.MIME_TYPE} == ? "
    val selectionArgs = arrayOf<String>()

    val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

    val query = query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
    )

    val dirs = ArrayList<DirectoryObject>()
    query?.use { cursor ->
        // Cache column indices
        val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val relativePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

        while(cursor.moveToNext()) {
            val dirObject = DirectoryObject().apply {
                id = UUID.randomUUID().toString()
                name = cursor.getString(displayNameColumn)
                relativePath = cursor.getString(relativePathColumn)
            }

            dirs.add(dirObject)
        }
    }

    return withContext(Dispatchers.IO) {
        dirs
    }
}

/**
 * Only use less for less than Android Q, API 29; otherwise, use getMediaStoreDirectories()
 */
@Suppress("DEPRECATION")
private suspend fun getPhotoDirsBelowQ(): ArrayList<DirectoryObject> {
    val dirs = ArrayList<DirectoryObject>()
    val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val children = dcim.listFiles(DirectoryFilter())
    children?.forEach { dir ->
        val dirObject = DirectoryObject().apply {
            id = UUID.randomUUID().toString()
            name = dir.name
            relativePath = dir.path

            Log.d("DIR_PATH", " absolute: ${dir.absolutePath}")
            Log.d("DIR_PATH", " canonical: ${dir.canonicalPath}")
        }
        dirs.add(dirObject)
    }
    return withContext(Dispatchers.IO) {
        dirs
    }
}

suspend fun Activity.getPhotoDirs(): ArrayList<DirectoryObject> {
    return if(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        getPhotoDirsBelowQ()
    } else {
        contentResolver.getMediaStoreDirectories()
    }
}

fun DirectoryObject.linkPhotosToDir(realm: Realm) {
    val childIds = RealmList<Long>()
    val photos = realm.copyFromRealm(realm.findAllPhotos())
    photos.forEach { photo ->

        // Get from URI if relativePath fails
        var parentPath = photo.uriString.substringBeforeLast(photo.name, "")
        Log.d("LINK PHOTOS", "parentPath from uri: $parentPath")

        if(parentPath.isEmpty()) {
            val file = File(photo.uriString)
            //parentPath = file.absolutePath
            Log.d("LINK PHOTOS", "absolute parentPath from uri file: $parentPath")
            if(parentPath.isEmpty()) {
                parentPath = file.canonicalPath
                Log.d("LINK PHOTOS", "canonical parentPath from uri file: $parentPath")
            }
        }

        //parentPath = photo.path.substringBeforeLast(photo.name, "")
        
        if(parentPath.isEmpty()) throw Throwable("parentPath is empty")
        
        parentPath = parentPath.replace("/", "")
        Log.d("LINK PHOTOS", "stripped parent path: $parentPath")
        
        val dirPath = relativePath.replace("/", "")
        Log.d("LINK PHOTOS", "stripped dir path, relative: $dirPath")
        
        if(dirPath.contains(parentPath)) {
            Log.d("LINK PHOTOS", "Directory Path contained file parent path")
            childIds.add(photo.id)
        } else if(parentPath.contains(dirPath)) {
            Log.d("LINK PHOTOS", "file parent path contained Directory Path")
            childIds.add(photo.id)
        } else if(parentPath.contains(name)) {
            Log.d("LINK PHOTOS", "file parent path contained dir name")
            childIds.add(photo.id)
        }
    }
    childPhotoIds = childIds
}