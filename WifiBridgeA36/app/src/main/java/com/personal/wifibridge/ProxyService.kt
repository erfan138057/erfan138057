package com.personal.wifibridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ProxyService : Service() {
    private val pool = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var password = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        password = intent?.getStringExtra("password") ?: password
        if (running.compareAndSet(false, true)) {
            if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("bridge", "Wi-Fi Bridge", NotificationManager.IMPORTANCE_LOW))
            val n = if (Build.VERSION.SDK_INT >= 26)
                Notification.Builder(this, "bridge").setContentTitle("Wi-Fi Bridge").setContentText("Proxy listening on port 8787").setSmallIcon(android.R.drawable.stat_sys_upload_done).build()
            else @Suppress("DEPRECATION") Notification.Builder(this).setContentTitle("Wi-Fi Bridge").setContentText("Proxy listening").setSmallIcon(android.R.drawable.stat_sys_upload_done).build()
            startForeground(7, n)
            pool.execute {
                try {
                    val s = ServerSocket()
                    server = s
                    s.reuseAddress = true
                    s.bind(InetSocketAddress("0.0.0.0", 8787), 32)
                    while (running.get()) pool.execute { handle(s.accept()) }
                } catch (_: Exception) { running.set(false) }
            }
        }
        return START_NOT_STICKY
    }

    private fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 30000
            val input = BufferedInputStream(c.getInputStream())
            val output = BufferedOutputStream(c.getOutputStream())
            try {
                val requestLine = readLine(input) ?: return
                val headers = mutableListOf<String>()
                var auth: String? = null
                var bytes = requestLine.length
                while (true) {
                    val line = readLine(input) ?: return
                    bytes += line.length
                    if (bytes > 32768) { respond(output, "431 Request Header Fields Too Large"); return }
                    if (line.isEmpty()) break
                    if (line.startsWith("Proxy-Authorization:", true)) auth = line.substringAfter(':').trim()
                    else if (!line.startsWith("Proxy-Connection:", true)) headers.add(line)
                }
                val expected = "Basic " + Base64.getEncoder().encodeToString(("phone:$password").toByteArray(Charsets.UTF_8))
                if (auth != expected) {
                    output.write("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"WiFiBridge\"\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()); output.flush(); return
                }
                val parts = requestLine.split(" ", limit = 3)
                if (parts.size < 2) { respond(output, "400 Bad Request"); return }
                if (parts[0].equals("CONNECT", true)) {
                    val hp = parts[1].split(":", limit = 2)
                    val host = hp[0]
                    val port = hp.getOrNull(1)?.toIntOrNull() ?: 443
                    Socket().use { upstream ->
                        upstream.connect(InetSocketAddress(host, port), 15000)
                        output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()); output.flush()
                        val t = Thread { pump(input, upstream.getOutputStream()) }; t.start()
                        pump(upstream.getInputStream(), output)
                        try { t.join(2000) } catch (_: Exception) {}
                    }
                } else {
                    val uri = try { URI(parts[1]) } catch (_: Exception) { null }
                    if (uri?.host == null || uri.scheme !in listOf("http", "https")) { respond(output, "400 Absolute URL Required"); return }
                    val port = if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
                    Socket().use { upstream ->
                        upstream.connect(InetSocketAddress(uri.host, port), 15000)
                        val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
                        val req = buildString {
                            append("${parts[0]} $path ${parts.getOrElse(2) { "HTTP/1.1" }}\r\n")
                            headers.forEach { append(it).append("\r\n") }
                            append("Connection: close\r\n\r\n")
                        }
                        upstream.getOutputStream().write(req.toByteArray(Charsets.ISO_8859_1)); upstream.getOutputStream().flush()
                        pump(upstream.getInputStream(), output)
                    }
                }
            } catch (_: Exception) { }
        }
    }
    private fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(32768)
        try { while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); output.flush() } } catch (_: Exception) {}
    }
    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (b.size() < 8192) { val c = input.read(); if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1"); if (c == 10) break; if (c != 13) b.write(c) }
        return b.toString("ISO-8859-1")
    }
    private fun respond(out: OutputStream, status: String) {
        out.write("HTTP/1.1 $status\r\nConnection: close\r\nContent-Length: 0\r\n\r\n".toByteArray()); out.flush()
    }
    override fun onDestroy() {
        running.set(false); try { server?.close() } catch (_: Exception) {}; pool.shutdownNow()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
