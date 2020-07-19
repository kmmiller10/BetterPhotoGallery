package me.kmmiller.better.photo.gallery

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.kmmiller.baseui.KmmBaseFragment
import me.kmmiller.better.photo.gallery.databinding.PhotoGridFragBinding
import me.kmmiller.better.photo.gallery.extensions.createGridItem

class PhotoGridFragment : KmmBaseFragment() {
    private lateinit var binding: PhotoGridFragBinding
    private lateinit var viewModel: PhotoGridViewModel

    private val mainActivity: MainActivity?
        get() = activity as? MainActivity

    private val realm: Realm?
        get() = mainActivity?.realm

    override fun getTitle(): String = viewModel.folderTitle
    private fun updateTitleFromFolderPath(title: String) {
        viewModel.folderTitle = title
        activity?.title = getTitle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PhotoGridViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = PhotoGridFragBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(binding.photoGrid) {
            adapter = PhotoGridAdapter()
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    val item = viewModel.photos[position]

                    /*if(item.isDir) {
                        openDirectoryAndLoadFiles(item.path)
                    } else {
                        // Todo display image
                        Log.d("PhotoGridFrag", "Display Image: ${item.path.substringAfterLast("/")}")
                    }*/
                }
        }

        if(checkStoragePermissions()) {
            getPhotos()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_REQUEST_CODE
            )
        }
    }

    override fun onStart() {
        super.onStart()
        updateAdapter()
    }

    private fun checkStoragePermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                && PackageManager.PERMISSION_GRANTED == requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun updateAdapter() {
        (binding.photoGrid.adapter as? PhotoGridAdapter)?.notifyDataSetChanged()
    }

    private fun getPhotos() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val photos = getPhotosFromContentResolver() // Detached list, write to realm
            withContext(Dispatchers.Main) {
                realm?.executeTransactionAsync { rlm ->
                    photos.forEach { photo ->
                        rlm.copyToRealmOrUpdate(photo)
                    }
                }

                activity?.contentResolver?.let { cr ->
                    photos.forEach { photo ->
                        viewModel.photos.add(photo.createGridItem(cr))
                    }
                }

                updateAdapter()
            }
        }
    }

    /**
     * Accesses media store and pulls in photos.
     * Content resolver info: https://developer.android.com/training/data-storage/shared/media
     * Must run on worker thread, see coroutine usage: https://developer.android.com/kotlin/coroutines
     */
    private suspend fun getPhotosFromContentResolver(index: Int = 0, count: Int = 20): ArrayList<PhotoObject> {
        val contentResolver = activity?.contentResolver ?: return arrayListOf()
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

        val selection = "" //""${MediaStore.Images.Media.MIME_TYPE} == ? "
        val selectionArgs = arrayOf<String>()

        val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

        val query = contentResolver.query(
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

            for(i in 0 until count) {
                cursor.moveToNext()
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
                }

                photos.add(photoObject)
            }
        }

        return withContext(Dispatchers.IO) {
            photos
        }
    }

    /*
    private suspend fun getDirectoriesFromContentResolver(): ArrayList<DirectoryObject> {
        val contentResolver = activity?.contentResolver ?: return arrayListOf()
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection = "" //""${MediaStore.Images.Media.MIME_TYPE} == ? "
        val selectionArgs = arrayOf<String>()

        val sortOrder = "${MediaStore.Images.Media.DISPLAY_NAME} ASC"

        val query = contentResolver.query(
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

            for(i in 0 until count) {
                cursor.moveToNext()
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
                }

                photos.add(photoObject)
            }
        }

        return withContext(Dispatchers.IO) {
            photos
        }
    }*/

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == STORAGE_REQUEST_CODE
            && (permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            || permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
            && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
            getPhotos()
        }
    }

    // Provides data for the adapter to draw on the UI
    data class PhotoGridItem(val name: String, val image: Bitmap, val photoObject: PhotoObject?, val dirObject: DirectoryObject?)

    inner class PhotoGridAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(requireContext())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.photo_cell, parent, false)
            val item = viewModel.photos[position]
            with(view.findViewById<AppCompatTextView>(R.id.photo_label)) {
                // Label
                text = item.name
            }

            with(view.findViewById<AppCompatImageView>(R.id.photo_image)) {
                // Image
                setImageBitmap(item.image)
            }
            return view
        }

        override fun getItem(position: Int): Any = viewModel.photos[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getCount(): Int = viewModel.photos.size
    }

    class PhotoGridViewModel: ViewModel() {
        var folderTitle = ""
        val photos = ArrayList<PhotoGridItem>()
    }

    companion object {
        private const val STORAGE_REQUEST_CODE = 100
    }
}