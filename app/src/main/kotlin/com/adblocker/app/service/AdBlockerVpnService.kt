package com.adblocker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adblocker.app.MainActivity
import com.adblocker.app.R
import com.adblocker.app.data.BlockStats
import com.adblocker.app.data.DomainStore
import com.adblocker.app.data.FilterCatalog
import com.adblocker.app.data.ListLoader
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Local VPN that only routes DNS queries (to a virtual DNS address, 10.8.0.1).
 * Blocked domains get an NXDOMAIN reply; everything else is forwarded to a real DNS server.
 * All other internet traffic never goes through the VPN.
 */
class AdBlockerVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.adblocker.app.STOP"
        private const val VPN_ADDRESS = "10.8.0.2"
        private const val VIRTUAL_DNS = "10.8.0.1"
        private val UPSTREAMS = listOf("1.1.1.1", "8.8.8.8")
        private const val CHANNEL_ID = "adblock_status"
        private const val NOTIF_ID = 1001
    }

    private val pool = Executors.newFixedThreadPool(8)
    private val writeLock = Any()

    @Volatile private var running = false
    @Volatile private var starting = false
    @Volatile private var out: FileOutputStream? = null
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        if (running || starting) return START_STICKY
        starting = true
        showNotification()
        Thread({ bootVpn() }, "vpn-boot").start()
        return START_STICKY
    }

    private fun bootVpn() {
        BlockStats.setPreparing(true)
        try {
            // Lists are bundled inside the app, so no internet is needed here.
            val chosen = FilterCatalog.all.filter { it.id in FilterCatalog.selectedIds(this) }
            BlockStats.setStatus("Preparing protection...")
            ListLoader.loadBundled(this, chosen) { }
            if (DomainStore.size.value == 0) {
                fail("Could not start protection. Check your internet connection.")
                return
            }

            val pfd = Builder()
                .setSession("Ad Blocker")
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .setMtu(1500)
                .establish()
            if (pfd == null) {
                fail("VPN permission was not granted")
                return
            }
            tun = pfd
            running = true
            BlockStats.setRunning(true)
            BlockStats.setStatus("Protection is on and blocking ads")
            Thread({ readLoop(pfd) }, "dns-loop").start()
        } catch (e: Exception) {
            fail("Error: ${e.message}")
        } finally {
            starting = false
            BlockStats.setPreparing(false)
        }
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        out = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try {
                input.read(buf)
            } catch (e: IOException) {
                -1
            }
            if (n < 0) break
            if (n == 0) continue
            val packet = buf.copyOf(n)
            try {
                pool.execute { handle(packet) }
            } catch (e: RejectedExecutionException) {
                break
            }
        }
        if (running) stopVpn()
    }

    private fun handle(packet: ByteArray) {
        val q = DnsPacket.parseQuery(packet) ?: return
        if (DomainStore.isBlocked(q.domain)) {
            BlockStats.onBlocked(q.domain)
            write(DnsPacket.blockedReply(q))
        } else {
            BlockStats.onAllowed()
            val answer = forward(q) ?: return
            write(DnsPacket.allowedReply(q, answer))
        }
    }

    /** Sends the DNS query to a real server over a protected socket (it bypasses the VPN). */
    private fun forward(q: DnsPacket.Query): ByteArray? {
        for (ip in UPSTREAMS) {
            try {
                DatagramSocket().use { sock ->
                    protect(sock)
                    sock.soTimeout = 3000
                    sock.connect(InetSocketAddress(InetAddress.getByName(ip), 53))
                    sock.send(DatagramPacket(q.dns, q.dns.size))
                    val buf = ByteArray(4096)
                    val reply = DatagramPacket(buf, buf.size)
                    sock.receive(reply)
                    return buf.copyOf(reply.length)
                }
            } catch (e: Exception) {
                // Try the next upstream server.
            }
        }
        return null
    }

    private fun write(packet: ByteArray) {
        val o = out ?: return
        synchronized(writeLock) {
            try {
                o.write(packet)
            } catch (e: IOException) {
                // The interface was closed; ignore.
            }
        }
    }

    private fun fail(msg: String) {
        BlockStats.setStatus(msg)
        BlockStats.setRunning(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopVpn() {
        running = false
        out = null
        try {
            tun?.close()
        } catch (e: IOException) {
            // Ignore.
        }
        tun = null
        BlockStats.setRunning(false)
        BlockStats.setStatus("Protection is off")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Ad Blocker is running")
            .setContentText("Blocking ads in all your apps")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        // Android 14+ requires a foreground service type; ServiceCompat ignores it on older versions.
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        running = false
        try {
            tun?.close()
        } catch (e: IOException) {
            // Ignore.
        }
        pool.shutdownNow()
        super.onDestroy()
    }
}
