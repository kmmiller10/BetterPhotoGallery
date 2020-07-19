package me.kmmiller.better.photo.gallery.extensions

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import me.kmmiller.better.photo.gallery.PhotoGridFragment
import me.kmmiller.better.photo.gallery.PhotoObject

@Suppress("DEPRECATION") // Branched to handle higher versions, but use deprecated on lower verions
fun PhotoObject.createGridItem(contentResolver: ContentResolver): PhotoGridFragment.PhotoGridItem {
    val uri = Uri.parse(uriString)

    val bitmap = if(Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
         MediaStore.Images.Media.getBitmap(contentResolver, uri)
    } else {
        val imageSource = ImageDecoder.createSource(contentResolver, uri)
        ImageDecoder.decodeBitmap(imageSource)
    }

    return PhotoGridFragment.PhotoGridItem (
        name = name,
        image = bitmap,
        photoObject = this,
        dirObject = null
    )
}