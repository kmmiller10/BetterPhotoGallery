package me.kmmiller.better.photo.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
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
import me.kmmiller.better.photo.gallery.extensions.*

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
            adapter = GridAdapter()
            onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    val item = viewModel.gridItems[position]

                    if(item.isDir) {
                        val dir = viewModel.dirs.firstOrNull { it.id == item.dirId} ?: return@OnItemClickListener
                        updateAdapter(dir.id)
                    } else {
                        val photo = viewModel.photos.firstOrNull { it.id == item.photoId} ?: return@OnItemClickListener
                        // todo
                        Log.d(PhotoGridFragment::class.java.simpleName, "Open Photo: ${photo.name}")
                    }
                }
        }

        if(savedInstanceState == null) {
            if (checkStoragePermissions()) {
                getPhotosAndDirs()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_REQUEST_CODE
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateAdapter()
    }

    private fun updateAdapter(dirId: String = "") {
        viewModel.gridItems.clear()
        viewModel.gridItems.addAll(buildGridItems(dirId))
        (binding.photoGrid.adapter as? GridAdapter)?.notifyDataSetChanged()
    }

    private fun buildGridItems(dirId: String): ArrayList<GridItem> {
        val cr = activity?.contentResolver ?: return arrayListOf()
        val gridItems = ArrayList<GridItem>()

        if(dirId.isEmpty()) {
            // At root level, show albums and root files
            // Add folders first
            val sortedDirs = viewModel.dirs.sortedBy { it.name }
            for(dir in sortedDirs) {
                gridItems.add(dir.createGridItem())
            }

            // Add root photos - id is not present in any directory
            val sortedPhotos = viewModel.photos.filter { photo ->
                photo.parentId.isEmpty()
            }.sortedBy { it.name }

            for(photo in sortedPhotos) {
                gridItems.add(photo.createGridItem(cr))
            }
        } else {
            val dir = viewModel.dirs.first { it.id == dirId }
            updateTitleFromFolderPath(dir.name)

            val sortedPhotos = viewModel.photos.filter { photo ->
                photo.parentId == dirId
            }.sortedBy { it.name }

            for(photo in sortedPhotos) {
                gridItems.add(photo.createGridItem(cr))
            }
        }
        return gridItems
    }

    private fun getPhotosAndDirs() {
        viewModel.photos.clear()
        viewModel.dirs.clear()
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val directories = getPhotoDirs()
            val photos = directories.getPhotosFromDirectories()

            withContext(Dispatchers.Main) {
                realm?.executeTransactionAsync { rlm ->
                    // Copy photos to realm
                    photos.forEach { photo ->
                        rlm.copyToRealmOrUpdate(photo)
                    }
                    // Copy dirs to realm
                    directories.forEach { dir ->
                        // Link child photos to parent directory
                        rlm.copyToRealmOrUpdate(dir)
                    }
                }

                viewModel.photos.addAll(photos)
                viewModel.dirs.addAll(directories)

                updateAdapter()
            }
        }
    }

    private fun checkStoragePermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == requireContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                && PackageManager.PERMISSION_GRANTED == requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == STORAGE_REQUEST_CODE
            && (permissions.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            || permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
            && grantResults[0] == PackageManager.PERMISSION_GRANTED ) {
            getPhotosAndDirs()
        } else {
            // TODO show error alert
        }
    }

    // Provides data for the adapter to draw on the UI
    data class GridItem(val name: String, val image: Bitmap?, var isDir: Boolean, var photoId: String = "", var dirId: String = "")

    inner class GridAdapter : BaseAdapter() {
        private val inflater = LayoutInflater.from(requireContext())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.photo_cell, parent, false)
            val item = viewModel.gridItems[position]
            with(view.findViewById<AppCompatTextView>(R.id.photo_label)) {
                // Label
                text = item.name
            }

            with(view.findViewById<AppCompatImageView>(R.id.photo_image)) {
                // Image
                if(item.isDir) {
                    setImageResource(R.drawable.ic_filled_folder)
                } else {
                    setImageBitmap(item.image)
                }
            }

            return view
        }

        override fun getItem(position: Int): Any = viewModel.gridItems[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getCount(): Int = viewModel.gridItems.size
    }

    class PhotoGridViewModel: ViewModel() {
        var folderTitle = ""
        val photos = ArrayList<PhotoObject>()
        val dirs = ArrayList<DirectoryObject>()
        val gridItems = ArrayList<GridItem>()
    }

    companion object {
        private const val STORAGE_REQUEST_CODE = 100
    }
}