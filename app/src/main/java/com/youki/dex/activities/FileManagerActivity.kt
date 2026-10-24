package com.youki.dex.activities

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.search.SearchBar
import com.youki.dex.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Lightweight file browser. Directory scans and file operations run off the UI thread. */
class FileManagerActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_ALL_FILES = 7211
        private const val REQUEST_STORAGE = 7212
        private const val REQUEST_SYSTEM_PICKER = 7213
    }

    private lateinit var storageRoot: File
    private lateinit var currentDirectory: File
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var emptyText: TextView
    private lateinit var itemCount: TextView
    private lateinit var selectionBar: View
    private lateinit var selectionCount: TextView
    private lateinit var fab: ExtendedFloatingActionButton
    private lateinit var searchBar: SearchBar
    private lateinit var breadcrumb: ViewGroup
    private lateinit var storageChips: ChipGroup
    private lateinit var sortButton: MaterialButton
    private lateinit var viewToggle: MaterialButton
    private lateinit var adapter: FileAdapter
    private var filesInFolder: List<File> = emptyList()
    private val selectedFiles = linkedSetOf<File>()
    private var showHiddenFiles = false
    private var gridMode = false
    private var sortMode = 0
    private var searchQuery = ""
    private var permissionPromptShown = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_file_manager)
        storageRoot = try { Environment.getExternalStorageDirectory().canonicalFile }
            catch (e: Exception) { Environment.getExternalStorageDirectory().absoluteFile }
        currentDirectory = storageRoot
        bindViews()
        configureRecycler()
        configureActions()
        buildStorageChips()
        updateBreadcrumb()
        if (hasStorageAccess()) refreshFiles() else renderPermissionState()
    }

    override fun onResume() {
        super.onResume()
        if (::storageRoot.isInitialized) {
            if (hasStorageAccess()) refreshFiles() else renderPermissionState()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            selectedFiles.isNotEmpty() -> clearSelection()
            currentDirectory.canonicalFile != storageRoot.canonicalFile ->
                currentDirectory.parentFile?.canonicalFile?.let { navigateTo(it) } ?: super.onBackPressed()
            else -> super.onBackPressed()
        }
    }

    private fun bindViews() {
        recycler = findViewById(R.id.fm_recycler)
        emptyState = findViewById(R.id.fm_empty_state)
        emptyText = findViewById(R.id.fm_empty_text)
        itemCount = findViewById(R.id.fm_item_count)
        selectionBar = findViewById(R.id.fm_selection_bar)
        selectionCount = findViewById(R.id.fm_selection_count)
        fab = findViewById(R.id.fm_fab)
        searchBar = findViewById(R.id.fm_search_bar)
        breadcrumb = findViewById(R.id.fm_breadcrumb)
        storageChips = findViewById(R.id.fm_storage_chips)
        sortButton = findViewById(R.id.fm_sort_btn)
        viewToggle = findViewById(R.id.fm_view_toggle)

        emptyState.setOnClickListener {
            if (!hasStorageAccess()) { permissionPromptShown = false; showPermissionDialog() }
        }
        emptyText.setOnClickListener {
            if (!hasStorageAccess()) { permissionPromptShown = false; showPermissionDialog() }
        }
        findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.fm_swipe_refresh)
            .setOnRefreshListener { refreshFiles() }
        fab.visibility = View.VISIBLE
        fab.setOnClickListener { showCreateFolderDialog() }
        findViewById<View>(R.id.fm_sel_cancel).setOnClickListener { clearSelection() }
        findViewById<View>(R.id.fm_sel_copy).setOnClickListener { chooseDestination(move = false) }
        findViewById<View>(R.id.fm_sel_move).setOnClickListener { chooseDestination(move = true) }
        findViewById<View>(R.id.fm_sel_share).setOnClickListener { shareFiles(selectedFiles.toList()) }
        findViewById<View>(R.id.fm_sel_compress).setOnClickListener { zipFiles(selectedFiles.toList()) }
        findViewById<View>(R.id.fm_sel_delete).setOnClickListener { confirmDelete(selectedFiles.toList()) }
        sortButton.setOnClickListener { showSortDialog() }
        viewToggle.setOnClickListener {
            gridMode = !gridMode
            configureLayoutManager()
            renderFiles()
        }
        searchBar.setOnClickListener { showSearchDialog() }
        searchBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.fm_action_select_all -> {
                    selectedFiles.clear()
                    selectedFiles.addAll(visibleFiles())
                    updateSelectionUi()
                    renderFiles()
                    true
                }
                R.id.fm_action_show_hidden -> {
                    showHiddenFiles = !showHiddenFiles
                    item.title = getString(if (showHiddenFiles) R.string.fm_hide_hidden else R.string.fm_show_hidden)
                    refreshFiles()
                    true
                }
                else -> false
            }
        }
        updateSelectionUi()
    }

    private fun configureRecycler() {
        adapter = FileAdapter(
            onClick = { file -> onFileClicked(file) },
            onLongClick = { file -> toggleSelection(file) },
            onMenu = { file, anchor -> showFileMenu(file, anchor) }
        )
        recycler.adapter = adapter
        configureLayoutManager()
    }

    private fun configureLayoutManager() {
        recycler.layoutManager = if (gridMode) GridLayoutManager(this, 2) else LinearLayoutManager(this)
        adapter.setGridMode(gridMode)
        viewToggle.setIconResource(
            if (gridMode) android.R.drawable.ic_menu_view else android.R.drawable.ic_menu_sort_by_size
        )
    }

    private fun buildStorageChips() {
        storageChips.removeAllViews()
        addStorageChip(getString(R.string.fm_internal_storage), storageRoot)
        val downloads = File(storageRoot, "Download")
        if (downloads.isDirectory) addStorageChip(getString(R.string.fm_downloads), downloads)
    }

    private fun addStorageChip(label: String, directory: File) {
        val chip = Chip(this).apply {
            id = View.generateViewId()
            text = label
            isCheckable = true
            isClickable = true
            setOnClickListener {
                storageChips.check(id)
                navigateTo(directory)
            }
        }
        if (storageChips.childCount == 0) chip.isChecked = true
        storageChips.addView(chip)
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else {
            val read = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val write = Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            read && write
        }

    private fun showPermissionDialog() {
        if (permissionPromptShown) {
            if (hasStorageAccess()) refreshFiles()
            return
        }
        permissionPromptShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.fm_permission_title)
            .setMessage(R.string.fm_permission_message)
            .setPositiveButton(R.string.fm_open_settings) { _, _ -> requestStorageAccess() }
            .setNeutralButton(R.string.fm_use_system_picker) { _, _ -> openSystemPicker() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> permissionPromptShown = false }
            .show()
    }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName)),
                    REQUEST_ALL_FILES
                )
            } catch (e: Exception) {
                try { startActivityForResult(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQUEST_ALL_FILES) }
                catch (inner: Exception) { Toast.makeText(this, R.string.fm_permission_missing, Toast.LENGTH_LONG).show() }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_STORAGE
            )
        }
    }

    private fun openSystemPicker() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, REQUEST_SYSTEM_PICKER)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.fm_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Activity result API retained for Android 8 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ALL_FILES -> if (hasStorageAccess()) refreshFiles() else renderPermissionState()
            REQUEST_SYSTEM_PICKER -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                val mime = contentResolver.getType(uri) ?: "*/*"
                try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (e: Exception) { Toast.makeText(this, R.string.fm_open_failed, Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE) {
            permissionPromptShown = false
            if (hasStorageAccess()) refreshFiles() else renderPermissionState()
        }
    }

    private fun renderPermissionState() {
        filesInFolder = emptyList()
        selectedFiles.clear()
        if (::adapter.isInitialized) adapter.submitFiles(emptyList(), emptySet())
        itemCount.text = getString(R.string.fm_permission_missing)
        emptyText.text = getString(R.string.fm_permission_missing)
        emptyState.visibility = View.VISIBLE
        fab.visibility = View.GONE
        updateSelectionUi()
        if (!permissionPromptShown) showPermissionDialog()
    }

    private fun isWithinRoot(file: File): Boolean = try {
        val path = file.canonicalPath
        val root = storageRoot.canonicalPath
        path == root || path.startsWith(root + File.separator)
    } catch (e: Exception) { false }

    private fun navigateTo(directory: File) {
        if (!directory.isDirectory || !isWithinRoot(directory)) {
            Toast.makeText(this, R.string.fm_operation_failed, Toast.LENGTH_SHORT).show()
            return
        }
        currentDirectory = directory.canonicalFile
        selectedFiles.clear()
        searchQuery = ""
        updateBreadcrumb()
        updateSelectionUi()
        refreshFiles()
    }

    private fun updateBreadcrumb() {
        breadcrumb.removeAllViews()
        val root = storageRoot.canonicalFile
        val parts = mutableListOf<File>()
        var cursor: File? = currentDirectory.canonicalFile
        while (cursor != null && isWithinRoot(cursor)) {
            parts.add(cursor)
            if (cursor == root) break
            cursor = cursor.parentFile
        }
        parts.reverse()
        parts.forEachIndexed { index, dir ->
            if (index > 0) breadcrumb.addView(TextView(this).apply {
                text = "›"
                textSize = 16f
                setPadding(dp(6), 0, dp(6), 0)
            })
            val label = if (dir == root) getString(R.string.fm_internal_storage) else dir.name
            breadcrumb.addView(TextView(this).apply {
                text = label
                textSize = 13f
                isClickable = true
                isFocusable = true
                setPadding(dp(5), dp(4), dp(5), dp(4))
                setOnClickListener { navigateTo(dir) }
            })
        }
    }

    private fun refreshFiles() {
        if (!hasStorageAccess()) { renderPermissionState(); return }
        val directory = currentDirectory
        val swipe = findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.fm_swipe_refresh)
        lifecycleScope.launch {
            val listed = withContext(Dispatchers.IO) {
                try { directory.listFiles()?.filter { showHiddenFiles || !it.name.startsWith(".") } ?: emptyList() }
                catch (e: Exception) { emptyList() }
            }
            swipe.isRefreshing = false
            if (directory != currentDirectory) return@launch
            filesInFolder = listed
            selectedFiles.retainAll(filesInFolder)
            renderFiles()
        }
    }

    private fun visibleFiles(): List<File> {
        val filtered = if (searchQuery.isBlank()) filesInFolder else filesInFolder.filter { it.name.contains(searchQuery, ignoreCase = true) }
        return filtered.sortedWith(Comparator { left, right ->
            if (left.isDirectory != right.isDirectory) {
                if (left.isDirectory) -1 else 1
            } else when (sortMode) {
                1 -> right.lastModified().compareTo(left.lastModified())
                2 -> right.length().compareTo(left.length())
                else -> left.name.compareTo(right.name, ignoreCase = true)
            }
        })
    }

    private fun renderFiles() {
        val visible = visibleFiles()
        adapter.submitFiles(visible, selectedFiles.map { it.absolutePath }.toSet())
        itemCount.text = getString(R.string.fm_items_count, visible.size)
        emptyState.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (searchQuery.isNotBlank() && visible.isEmpty()) getString(R.string.fm_no_search_results) else getString(R.string.folder_empty)
        fab.visibility = if (hasStorageAccess()) View.VISIBLE else View.GONE
        updateSelectionUi()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.fm_select_current_folder_search)
            setText(searchQuery)
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.fm_select_current_folder_search)
            .setView(input)
            .setPositiveButton(android.R.string.search_go) { _, _ -> searchQuery = input.text.toString().trim(); renderFiles() }
            .setNeutralButton(R.string.fm_clear_search) { _, _ -> searchQuery = ""; renderFiles() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSortDialog() {
        val labels = arrayOf(getString(R.string.fm_sort_name), getString(R.string.fm_sort_modified), getString(R.string.fm_sort_size))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.fm_sort_name)
            .setSingleChoiceItems(labels, sortMode) { dialog, which ->
                sortMode = which
                sortButton.text = labels[which]
                dialog.dismiss()
                renderFiles()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun onFileClicked(file: File) {
        if (selectedFiles.isNotEmpty()) toggleSelection(file)
        else if (file.isDirectory) navigateTo(file) else openFile(file)
    }

    private fun toggleSelection(file: File) {
        if (!selectedFiles.add(file)) selectedFiles.remove(file)
        updateSelectionUi()
        renderFiles()
    }

    private fun clearSelection() {
        selectedFiles.clear()
        updateSelectionUi()
        renderFiles()
    }

    private fun updateSelectionUi() {
        if (!::selectionBar.isInitialized) return
        selectionBar.visibility = if (selectedFiles.isEmpty()) View.GONE else View.VISIBLE
        selectionCount.text = getString(R.string.fm_selected_count, selectedFiles.size)
        if (::adapter.isInitialized) adapter.setSelectedPaths(selectedFiles.map { it.absolutePath }.toSet())
        if (::searchBar.isInitialized) {
            searchBar.menu.findItem(R.id.fm_action_select_all)?.isVisible = selectedFiles.isEmpty()
            searchBar.menu.findItem(R.id.fm_action_show_hidden)?.title = getString(if (showHiddenFiles) R.string.fm_hide_hidden else R.string.fm_show_hidden)
        }
    }

    private fun showFileMenu(file: File, anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, getString(R.string.fm_open))
        menu.menu.add(0, 2, 1, getString(R.string.fm_rename))
        menu.menu.add(0, 3, 2, getString(R.string.fm_details))
        menu.menu.add(0, 4, 3, getString(R.string.share))
        menu.menu.add(0, 5, 4, getString(R.string.delete))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> if (file.isDirectory) navigateTo(file) else openFile(file)
                2 -> showRenameDialog(file)
                3 -> showDetails(file)
                4 -> shareFiles(listOf(file))
                5 -> confirmDelete(listOf(file))
            }
            true
        }
        menu.show()
    }

    private fun showCreateFolderDialog() {
        if (!hasStorageAccess()) { showPermissionDialog(); return }
        val input = EditText(this).apply { hint = getString(R.string.fm_new_folder_name); setSingleLine(true) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setMessage(R.string.fm_create_folder_prompt)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (!isValidName(name)) Toast.makeText(this, R.string.fm_invalid_folder_name, Toast.LENGTH_SHORT).show()
                else if (File(currentDirectory, name).mkdir()) refreshFiles()
                else Toast.makeText(this, R.string.fm_create_failed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this).apply { setText(file.name); setSingleLine(true); setSelectAllOnFocus(true) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.fm_rename)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (!isValidName(name)) Toast.makeText(this, R.string.fm_invalid_folder_name, Toast.LENGTH_SHORT).show()
                else {
                    val target = File(file.parentFile, name)
                    if (!target.exists() && file.renameTo(target)) refreshFiles()
                    else Toast.makeText(this, R.string.fm_operation_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\') && !name.contains('\u0000')

    private fun showDetails(file: File) {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
        val size = if (file.isDirectory) getString(R.string.fm_internal_storage) else formatSize(file.length())
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage(getString(R.string.fm_file_details, file.absolutePath, size, date))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmDelete(files: List<File>) {
        if (files.isEmpty()) return
        val message = if (files.size == 1) getString(R.string.fm_delete_confirm, files.first().name)
            else getString(R.string.fm_delete_selected_confirm, files.size)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                runFileOperation {
                    var deleted = 0
                    files.forEach { file ->
                        if (isWithinRoot(file) && file != storageRoot && (if (file.isDirectory) file.deleteRecursively() else file.delete())) deleted++
                    }
                    deleted
                }
            }
            .show()
    }

    private fun chooseDestination(move: Boolean) {
        val files = selectedFiles.toList()
        if (files.isNotEmpty()) showDestinationDialog(storageRoot, files, move)
    }

    private fun showDestinationDialog(directory: File, files: List<File>, move: Boolean) {
        val children = try {
            directory.listFiles()?.filter { it.isDirectory && !it.isHidden && isWithinRoot(it) }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        } catch (e: Exception) { emptyList() }
        val labels = mutableListOf(getString(R.string.fm_use_this_folder))
        val hasParent = directory.canonicalFile != storageRoot.canonicalFile
        if (hasParent) labels.add("← ..")
        labels.addAll(children.map { it.name })
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.fm_destination) + ": " + directory.name.ifBlank { getString(R.string.fm_internal_storage) })
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which == 0 -> performCopyMove(files, directory, move)
                    hasParent && which == 1 -> directory.parentFile?.let { showDestinationDialog(it, files, move) }
                    else -> {
                        val childIndex = which - if (hasParent) 2 else 1
                        children.getOrNull(childIndex)?.let { showDestinationDialog(it, files, move) }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performCopyMove(files: List<File>, destination: File, move: Boolean) {
        runFileOperation {
            var completed = 0
            for (source in files) {
                try {
                    if (!isWithinRoot(source) || !isWithinRoot(destination)) continue
                    val sourcePath = source.canonicalPath
                    val destPath = destination.canonicalPath
                    if (destPath == sourcePath || destPath.startsWith(sourcePath + File.separator) || destPath == source.parentFile?.canonicalPath) continue
                    val target = File(destination, source.name)
                    if (target.exists()) continue
                    if (move) {
                        if (!source.renameTo(target)) {
                            source.copyRecursively(target, overwrite = false)
                            if (!source.deleteRecursively()) throw IllegalStateException("Could not delete source after copy")
                        }
                    } else source.copyRecursively(target, overwrite = false)
                    completed++
                } catch (e: Exception) {}
            }
            completed
        }
    }

    private fun shareFiles(files: List<File>) {
        if (files.isEmpty()) return
        try {
            val sourceFiles = files.filter { it.isFile && isWithinRoot(it) }
            val uris = ArrayList<Uri>()
            sourceFiles.forEach { uris.add(FileProvider.getUriForFile(this, packageName + ".provider", it)) }
            if (uris.isEmpty()) return
            val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply {
                type = mimeType(sourceFiles.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newUri(contentResolver, sourceFiles.first().name, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.fm_operation_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun zipFiles(files: List<File>) {
        if (files.isEmpty()) return
        runFileOperation {
            val output = File(currentDirectory, "DevDex_" + System.currentTimeMillis() + ".zip")
            var count = 0
            ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
                files.forEach { file -> if (isWithinRoot(file) && file != output) { addToZip(zip, file, file.name); count++ } }
            }
            if (count == 0) { output.delete(); 0 } else count
        }
    }

    private fun addToZip(zip: ZipOutputStream, file: File, name: String) {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return
            if (children.isEmpty()) {
                zip.putNextEntry(ZipEntry(name.trimEnd('/') + "/"))
                zip.closeEntry()
            } else children.forEach { child -> if (isWithinRoot(child)) addToZip(zip, child, name + "/" + child.name) }
        } else {
            zip.putNextEntry(ZipEntry(name))
            BufferedInputStream(FileInputStream(file)).use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, packageName + ".provider", file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.fm_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.getDefault())) ?: "*/*"

    private fun runFileOperation(operation: suspend () -> Int) {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) { try { operation() } catch (e: Exception) { 0 } }
            selectedFiles.clear()
            refreshFiles()
            Toast.makeText(this@FileManagerActivity,
                if (count > 0) getString(R.string.fm_operation_complete, count) else getString(R.string.fm_operation_failed),
                Toast.LENGTH_LONG).show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return bytes.toString() + " B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
    }

    private class FileAdapter(
        private val onClick: (File) -> Unit,
        private val onLongClick: (File) -> Unit,
        private val onMenu: (File, View) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.FileHolder>() {
        private var items: List<File> = emptyList()
        private var selectedPaths: Set<String> = emptySet()
        private var grid = false
        init { setHasStableIds(true) }
        fun submitFiles(files: List<File>, selected: Set<String>) {
            items = files
            selectedPaths = selected
            notifyDataSetChanged()
        }
        fun setSelectedPaths(selected: Set<String>) { selectedPaths = selected; notifyDataSetChanged() }
        fun setGridMode(value: Boolean) { grid = value; notifyDataSetChanged() }
        override fun getItemId(position: Int): Long = items[position].absolutePath.hashCode().toLong()
        override fun getItemViewType(position: Int): Int = if (grid) 1 else 0
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileHolder {
            val layout = if (viewType == 1) R.layout.item_fm_file_grid else R.layout.item_fm_file_list
            return FileHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
        }
        override fun onBindViewHolder(holder: FileHolder, position: Int) {
            val file = items[position]
            val ctx = holder.itemView.context
            holder.name.text = file.name.ifBlank { ctx.getString(R.string.fm_internal_storage) }
            holder.size.text = if (file.isDirectory) ctx.getString(R.string.fm_internal_storage) else fileSize(file.length())
            holder.date.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            holder.icon.setImageResource(if (file.isDirectory) android.R.drawable.ic_menu_agenda else when (file.extension.lowercase(Locale.getDefault())) {
                "jpg", "jpeg", "png", "gif", "webp", "bmp" -> android.R.drawable.ic_menu_gallery
                "mp4", "mkv", "webm", "mov", "mp3", "wav", "ogg" -> android.R.drawable.ic_media_play
                "zip", "7z", "rar", "gz" -> android.R.drawable.ic_menu_save
                else -> android.R.drawable.ic_menu_edit
            })
            (holder.itemView as? MaterialCardView)?.apply {
                isCheckable = true
                isChecked = selectedPaths.contains(file.absolutePath)
                strokeWidth = if (isChecked) dp(ctx, 2) else 0
                strokeColor = ctx.getColor(android.R.color.holo_blue_light)
            }
            holder.itemView.setOnClickListener { onClick(file) }
            holder.itemView.setOnLongClickListener { onLongClick(file); true }
            holder.menu.setOnClickListener { onMenu(file, it) }
        }
        override fun getItemCount(): Int = items.size
        class FileHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.fm_item_name)
            val size: TextView = view.findViewById(R.id.fm_item_size)
            val date: TextView = view.findViewById(R.id.fm_item_date)
            val icon: ImageView = view.findViewById(R.id.fm_item_icon)
            val menu: View = view.findViewById(R.id.fm_item_menu)
        }
        companion object {
            private fun dp(context: android.content.Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
            private fun fileSize(bytes: Long): String {
                if (bytes < 1024) return bytes.toString() + " B"
                val units = arrayOf("KB", "MB", "GB", "TB")
                var value = bytes.toDouble()
                var index = -1
                do { value /= 1024.0; index++ } while (value >= 1024.0 && index < units.lastIndex)
                return String.format(Locale.getDefault(), "%.1f %s", value, units[index])
            }
        }
    }
}
