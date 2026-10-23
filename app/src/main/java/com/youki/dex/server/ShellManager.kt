package com.youki.dex.server

import android.content.Context
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.security.SecureRandom

/**
 * ShellManager — client for the ShellServer socket on localhost:7171.
 *
 * SECURITY (patched): the session token is generated here, kept in the
 * app's private SharedPreferences and passed to ShellServer through the
 * YOUKI_SHELL_TOKEN environment variable when it is launched. It is never
 * broadcast. (Old design: ShellServer sent the token with an implicit
 * `am broadcast`, which any other app could receive → root/shell RCE.)
 * Because the token is persistent per install, a server that is still
 * running from an earlier app session keeps working after the app restarts.
 *
 * Usage:
 *   ShellManager.exec(context, "am task remove 42") { result -> ... }
 *   val result = ShellManager.execSync("am task list")
 */
object ShellManager {

    private const val HOST  = "127.0.0.1"
    private const val PORT  = 7171
    private const val MAGIC = "YOUKI_SHELL_V1"

    private const val PREFS_NAME = "youki_shell_private"
    private const val KEY_TOKEN  = "token"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var sessionToken: String? = null
    @Volatile private var isConnected: Boolean = false

    private fun loadOrCreateToken(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.length >= 32 }?.let { return it }
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    // ─── Init ───────────────────────────────────────────────────────────────
    fun init(context: Context) {
        sessionToken = loadOrCreateToken(context)
        isConnected = false
        // Try connecting in case a server from a previous session is still alive.
        scope.launch { ping() }
    }

    /** Called from the service's onDestroy(). */
    fun destroy() {
        scope.coroutineContext[Job]?.cancelChildren()
        isConnected = false
    }

    val isAvailable: Boolean get() = sessionToken != null && isConnected

    // ─── Async exec ────────────────────────────────────────────────────────
    fun exec(
        context: Context? = null,
        cmd: String,
        onResult: (String) -> Unit
    ) {
        scope.launch {
            val result = execSync(cmd)
            withContext(Dispatchers.Main) { onResult(result ?: "") }
        }
    }

    // ─── Sync exec (call from IO thread only) ──────────────────────────────
    fun execSync(cmd: String): String? {
        val token = sessionToken ?: return null
        return try {
            Socket(HOST, PORT).use { socket ->
                socket.soTimeout = 15_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                if (reader.readLine() != MAGIC) return null
                writer.println("TOKEN:$token")
                if (reader.readLine() != "TOKEN_OK") return null

                isConnected = true

                // base64 so multi-line commands can't inject protocol lines
                val encoded = java.util.Base64.getEncoder()
                    .encodeToString(cmd.toByteArray(Charsets.UTF_8))
                writer.println("CMDB64:$encoded")

                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line == "__DONE__") break
                    if (sb.isNotEmpty()) sb.append('\n')
                    sb.append(line)
                }

                writer.println("EXIT")
                sb.toString().trim()
            }
        } catch (e: Exception) {
            isConnected = false
            null
        }
    }

    // ─── Ping to check connection ───────────────────────────────────────────
    private fun ping(): Boolean {
        val token = sessionToken ?: return false
        return try {
            Socket(HOST, PORT).use { socket ->
                socket.soTimeout = 3_000
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                if (reader.readLine() != MAGIC) return false
                writer.println("TOKEN:$token")
                if (reader.readLine() != "TOKEN_OK") return false
                writer.println("PING")
                val pong = reader.readLine()
                writer.println("EXIT")
                (pong == "PONG").also { isConnected = it }
            }
        } catch (e: Exception) {
            isConnected = false
            false
        }
    }

    /** After launching the server, poll until it answers (it has no broadcast any more). */
    private fun awaitServer() {
        scope.launch {
            repeat(12) {
                delay(500)
                if (ping()) return@launch
            }
        }
    }

    private fun launchCommand(context: Context): String? {
        val token = sessionToken ?: loadOrCreateToken(context).also { sessionToken = it }
        val apk = context.packageCodePath
        // single-quote the path; the token is hex only
        val safeApk = apk.replace("'", "'\\''")
        return "YOUKI_SHELL_TOKEN=$token CLASSPATH='$safeApk' app_process / com.youki.dex.server.ShellServer"
    }

    /**
     * Launches ShellServer via Shizuku (only once, as a bootstrap).
     */
    fun launchViaShizuku(context: Context) {
        if (isAvailable) return
        val shizuku = com.youki.dex.utils.ShizukoManager.getInstance(context)
        if (!shizuku.hasPermission) return
        val cmd = launchCommand(context) ?: return
        shizuku.runShell(cmd) {}
        awaitServer()
    }

    /** Launches ShellServer via Root. */
    fun launchViaRoot(context: Context) {
        if (isAvailable) return
        val root = com.youki.dex.utils.RootManager.getInstance(context)
        if (!root.isAvailable) return
        val cmd = launchCommand(context) ?: return
        root.runShell("$cmd &") {}
        awaitServer()
    }
}
