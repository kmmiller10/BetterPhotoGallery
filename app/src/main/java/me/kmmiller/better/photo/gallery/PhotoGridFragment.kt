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

class PhotoGridFragment : KmmBaseFragment(), BackPressFragment {
    private lateinit var binding: PhotoGridFragBinding
    private lateinit var viewModel: PhotoGridViewModel

    private val mainActivity: MainActivity?
        get() = activity as? MainActivity

    private val realm: Realm?
        get() = mainActivity?.realm

    override fun getTitle(): String = viewModel.folderTitle
    private fun setTitleFromPath(title: String) {
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
                        openDirectory(dir.id, true)
                    } else {
                        val photo = viewModel.photos.firstOrNull { it.id == item.photoId} ?: return@OnItemClickListener
                        // todo
                        Log.d(PhotoGridFragment::class.java.simpleName, "Open Photo: ${photo.name}")
                    }
                }
        }

        if(savedInstanceState == null) {
            if(!restoreCachedFiles()) {
                onRefresh()
            }
        }

        binding.swipeLayout.setOnRefreshListener {
            onRefresh()
        }
    }

    private fun onRefresh() {
        binding.swipeLayout.isRefreshing = true
        if (checkStoragePermissions()) {
            getFilesFromStorage()
            getThumbnailPhotos()
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

    override fun onBackPress(): Boolean {
        if(viewModel.path.size == 1) return false // at root, return
        viewModel.path.removeAt(viewModel.path.size - 1) // pop current dir
        openDirectory(viewModel.path.last(), false) // open previous dir
        mainActivity?.updateToolbarBackBtn(viewModel.showBack) // update back button
        binding.swipeLayout.isEnabled = !viewModel.showBack // enabled/disable swipe to refresh
        return true
    }

    private fun openDirectory(dirId: String, addToPath: Boolean) {
        // Local function so it can't be called from fragment scope
        fun buildGridItems(dirId: String, addToPath: Boolean): ArrayList<GridItem> {
            if(addToPath) viewModel.path.add(dirId)
            val cr = activity?.contentResolver ?: return arrayListOf()
            val gridItems = ArrayList<GridItem>()

            if(dirId.isEmpty()) {
                // At root level, show albums and root files
                setTitleFromPath("DCIM")
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
                    gridItems.add(photo.createGridItem(cr, realm))
                }
            } else {
                val dir = viewModel.dirs.first { it.id == dirId }
                setTitleFromPath(dir.name)

                val sortedPhotos = viewModel.photos.filter { photo ->
                    photo.parentId == dirId
                }.sortedBy { it.name }

                for(photo in sortedPhotos) {
                    gridItems.add(photo.createGridItem(cr, realm))
                }
            }
            return gridItems
        }

        viewModel.gridItems.clear()
        viewModel.gridItems.addAll(buildGridItems(dirId, addToPath))
        (binding.photoGrid.adapter as? GridAdapter)?.notifyDataSetChanged()

        mainActivity?.updateToolbarBackBtn(viewModel.showBack)
        binding.swipeLayout.isEnabled = !viewModel.showBack
    }

    private fun restoreCachedFiles(): Boolean {
        val rlm = realm ?: return false
        viewModel.photos.clear()
        viewModel.dirs.clear()
        viewModel.thumbnailPhotos.clear()
        viewModel.thumbnailDirs.clear()

        val allPhotos = rlm.copyFromRealm(rlm.findAllPhotos())
        val allDirs = rlm.copyFromRealm(rlm.findAllDirectories())
        if(viewModel.photos.isEmpty() || viewModel.dirs.isEmpty()) return false

        val thumbnailPhotos = allPhotos.filter { it.isThumbnail }
        val thumbnailDirs = allDirs.filter { it.isThumbnailDir }
        viewModel.thumbnailPhotos.addAll(thumbnailPhotos)
        viewModel.thumbnailDirs.addAll(thumbnailDirs)

        val photos = allPhotos.filter { !it.isThumbnail }
        val dirs = allDirs.filter { !it.isThumbnailDir }
        viewModel.photos.addAll(photos)
        viewModel.dirs.addAll(dirs)

        openDirectory("", true)
        return true
    }

    private fun getFilesFromStorage() {
        viewModel.path.clear()
        viewModel.photos.clear()
        viewModel.dirs.clear()

        // Remove old cached files since the new ones will have unique ids
        // TODO improve this behavior by finding existing files on refresh by path/uriString
        realm?.executeTransaction {
            it.deletePhotosFromRealm()
            it.deleteDirsFromRealm()
        }

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
                        rlm.copyToRealmOrUpdate(dir)
                    }
                }

                viewModel.photos.addAll(photos)
                viewModel.dirs.addAll(directories)

                openDirectory("", true)
                binding.swipeLayout.isRefreshing = false
            }
        }
    }

    private fun getThumbnailPhotos() {
        viewModel.thumbnailPhotos.clear()
        viewModel.thumbnailDirs.clear()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val thumbnailDirs = getThumbnailDirs()
            val thumbnailPhotos = thumbnailDirs.getPhotosFromDirectories().apply {
                forEach {
                    it.isThumbnail = true
                }
            }

            withContext(Dispatchers.Main) {
                realm?.executeTransactionAsync { rlm ->
                    // Copy photos to realm
                    thumbnailPhotos.forEach { photo ->
                        rlm.copyToRealmOrUpdate(photo)
                    }
                    // Copy dirs to realm
                    thumbnailDirs.forEach { dir ->
                        rlm.copyToRealmOrUpdate(dir)
                    }
                }

                viewModel.thumbnailPhotos.addAll(thumbnailPhotos)
                viewModel.thumbnailDirs.addAll(thumbnailDirs)
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
            || permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))) {
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getFilesFromStorage()
            } else {
                showAlert(getString(R.string.permission_required), getString(R.string.storage_access_required))
            }
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
        val path = ArrayList<String>()
        val photos = ArrayList<PhotoObject>()
        val dirs = ArrayList<DirectoryObject>()
        val gridItems = ArrayList<GridItem>()

        val thumbnailPhotos = ArrayList<PhotoObject>()
        val thumbnailDirs = ArrayList<DirectoryObject>()

        val showBack: Boolean
            get() = path.size > 1
    }

    companion object {
        private const val STORAGE_REQUEST_CODE = 100
    }
}