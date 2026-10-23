package com.youki.dex.plugins

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.youki.dex.R
import com.youki.dex.utils.RootManager
import com.youki.dex.utils.ShizukoManager
import kotlinx.coroutines.*
import java.io.File
import java.util.zip.ZipInputStream

/**
 * PluginInstaller — installs one or more mods in parallel.
 *
 * The logic is exactly like Vexiro:
 *  1) Copies the ZIP to a temp folder (because Shell can't read content:// URIs)
 *  2) Runs the "unzip" command via Shizuku/Root (same approach as Vexiro)
 *  3) If there's no shell → uses ZipInputStream as a fallback
 *  4) Runs install.sh after extraction if it exists
 *  5) Sets the permissions on the scripts
 */
object PluginInstaller {

    // ── Installation states ───────────────────────────────────────────────────
    sealed class InstallState {
        object Queued                           : InstallState()
        data class Progress(val step: String, val pct: Int) : InstallState()
        data class Success(val plugin: YoukiPlugin)         : InstallState()
        data class Failed (val reason: String)              : InstallState()
    }

    // ── Install multiple in parallel ─────────────────────────────────────────
    suspend fun installAll(
        context:  Context,
        uris:     List<Uri>,
        onUpdate: (uri: Uri, state: InstallState) -> Unit
    ) = coroutineScope {
        uris.map { uri ->
            async(Dispatchers.IO) {
                installOne(context, uri) { state ->
                    CoroutineScope(Dispatchers.Main).launch { onUpdate(uri, state) }
                }
            }
        }.awaitAll()
    }

    // ── Install one ZIP ──────────────────────────────────────────────────────
    private fun installOne(
        context: Context,
        uri:     Uri,
        onState: (InstallState) -> Unit
    ) {
        onState(InstallState.Progress(context.getString(R.string.install_step_preparing), 5))

        // 1) Get filename
        val displayName = getDisplayName(context, uri) ?: "plugin.zip"
        if (!displayName.lowercase().endsWith(".zip")) {
            onState(InstallState.Failed(context.getString(R.string.install_error_not_zip))); return
        }

        val pluginId = sanitizeId(displayName.substringBeforeLast('.'))
        val destDir  = PluginManager.pluginDir(context, pluginId)
        if (destDir.exists()) destDir.deleteRecursively()
        destDir.mkdirs()

        // 2) Copy ZIP to private cache (shell can't read content:// URIs)
        onState(InstallState.Progress(context.getString(R.string.install_step_copying), 15))
        val cacheZip = File(context.cacheDir, "install_tmp_$pluginId.zip")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheZip.outputStream().use { input.copyTo(it) }
            } ?: throw Exception(context.getString(R.string.install_error_read_failed))
        } catch (e: Exception) {
            destDir.deleteRecursively()
            onState(InstallState.Failed(context.getString(R.string.install_error_copy_failed, e.message))); return
        }

        // 3) Extract — Shell unzip (like Vexiro) or ZipInputStream as a fallback
        onState(InstallState.Progress(context.getString(R.string.install_step_extracting), 30))
        val extracted = extractZip(context, cacheZip, destDir) { step ->
            onState(InstallState.Progress(step, 55))
        }
        cacheZip.delete()  // clean up the cache

        if (!extracted) {
            destDir.deleteRecursively()
            onState(InstallState.Failed(context.getString(R.string.install_error_extract_failed))); return
        }

        // 4) Validate — any single file existing in the folder is enough
        onState(InstallState.Progress(context.getString(R.string.install_step_validating), 70))
        val hasAnyFile = destDir.walkTopDown()
            .filter { it.isFile }
            .any()

        if (!hasAnyFile) {
            destDir.deleteRecursively()
            onState(InstallState.Failed(context.getString(R.string.install_error_empty_archive)))
            return
        }

        // 5) Fix permissions on the scripts
        listOf("service.sh", "disable.sh", "install.sh", "uninstall.sh").forEach {
            File(destDir, it).takeIf { f -> f.exists() }?.setExecutable(true, false)
        }

        // 6) Run install.sh if it exists (like Magisk modules)
        onState(InstallState.Progress(context.getString(R.string.install_step_running_script), 85))
        val installScript = File(destDir, "install.sh")
        if (installScript.exists()) {
            runScript(context, installScript.absolutePath)
        }

        onState(InstallState.Progress(context.getString(R.string.install_step_done), 100))

        // Read the plugin's data — if it fails, create a default plugin named after the folder
        val plugin = PluginManager.readPlugin(context, destDir)
            ?: YoukiPlugin(
                id = pluginId, name = pluginId, description = "",
                version = "1.0", author = "Unknown", path = destDir.absolutePath
            )

        onState(InstallState.Success(plugin))
    }

    // ── Extract ZIP — shell unzip first, ZipInputStream as fallback ──────────
    private fun extractZip(
        context: Context,
        zipFile: File,
        destDir: File,
        onStep:  (String) -> Unit
    ): Boolean {
        // SECURITY (patched): extraction now ALWAYS happens in-process with the app's own
        // (unprivileged) permissions. The old "Method 1" ran `unzip` as root/Shizuku:
        // real unzip restores symlinks, and the canonicalPath pre-check above it cannot
        // see a symlink that only exists after extraction, so a crafted ZIP could write
        // outside destDir with root privileges. The in-process extractor below only ever
        // creates regular files and checks every path; root is used later solely to run
        // the plugin's own install.sh (which is what a plugin is meant to do).
        val maxEntries = 5000
        val maxTotalBytes = 300L * 1024 * 1024
        var totalBytes = 0L

        onStep(context.getString(R.string.install_step_extracting))
        return try {
            var count = 0
            val destCanonicalPath = destDir.canonicalPath + File.separator
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = File(destDir, entry.name)
                        // Fix for Critical Security — Zip Slip / Path Traversal:
                        // entry.name comes from an untrusted external ZIP file
                        // (the user downloads it from external sources). If it
                        // contained "../../..", File(destDir, entry.name) used
                        // to write completely outside destDir — potentially
                        // reaching the app's internal SharedPreferences files
                        // or other sensitive files inside the app's sandbox.
                        // This is a well-known vulnerability, historically
                        // documented in many unzip tools (Zip Slip). Fix: we
                        // verify that the final path after normalization
                        // (canonicalPath) actually starts with destDir's path
                        // before any write — any entry trying to escape
                        // outside the plugin's designated folder is silently
                        // ignored.
                        if (!out.canonicalPath.startsWith(destCanonicalPath)) {
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        }
                        if (count >= maxEntries) return@use
                        out.parentFile?.mkdirs()
                        // size-capped copy (zip-bomb guard)
                        out.outputStream().use { os ->
                            val buf = ByteArray(16 * 1024)
                            while (true) {
                                val n = zip.read(buf)
                                if (n < 0) break
                                totalBytes += n
                                if (totalBytes > maxTotalBytes) throw java.io.IOException("ZIP too large")
                                os.write(buf, 0, n)
                            }
                        }
                        count++
                        if (count % 3 == 0) onStep(context.getString(R.string.install_step_extracted_count, count))
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            count > 0
        } catch (e: Exception) { false }
    }

    // ── Run a shell script ───────────────────────────────────────────────────
    private fun runScript(context: Context, scriptPath: String) {
        val cmd     = "sh '$scriptPath'"
        val root    = RootManager.getInstance(context)
        val shizuku = ShizukoManager.getInstance(context)
        when {
            root.isAvailable      -> root.runShellSync(cmd)
            shizuku.hasPermission -> shizuku.runShellSync(cmd)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun getDisplayName(context: Context, uri: Uri): String? =
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                if (idx >= 0) c.getString(idx) else null
            }
        } catch (e: Exception) { null }

    private fun sanitizeId(raw: String): String {
        val cleaned = raw.map { c -> if (c.isLetterOrDigit() && c.code < 128 || c == '_' || c == '-') c else '_' }
            .joinToString("")
        val capped = cleaned.take(64)
        return capped.ifEmpty { "plugin" }
    }
}
