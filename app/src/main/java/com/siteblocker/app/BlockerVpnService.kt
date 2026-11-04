package com.siteblocker.app

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Local "VPN" that never leaves the phone. Only DNS traffic is routed into it.
 * For every DNS question: if the domain is on the block list we answer "does not exist",
 * otherwise we forward the question to a real DNS server and pass the answer back.
 */
class BlockerVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.siteblocker.app.START"
        const val ACTION_STOP = "com.siteblocker.app.STOP"
        private const val VPN_DNS = "10.0.0.1"
        private val UPSTREAM_DNS = byteArrayOf(8, 8, 8, 8)
    }

    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private val pool = Executors.newCachedThreadPool()
    private val writeLock = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null && !BlockList.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        val fd = Builder()
            .setSession("Site Blocker")
            .addAddress("10.0.0.2", 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32) // only DNS goes through us; everything else is untouched
            .setBlocking(true)
            .establish() ?: return
        tun = fd
        running = true
        worker = Thread { runLoop(fd) }.also { it.start() }
    }

    private fun runLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n < 0) break
                if (n == 0) continue
                handlePacket(buf.copyOf(n), output)
            }
        } catch (e: Exception) {
            // interface closed
        } finally {
            stopVpn()
        }
    }

    private fun handlePacket(p: ByteArray, out: FileOutputStream) {
        if (p.size < 28) return
        if ((p[0].toInt() shr 4) != 4) return // IPv4 only
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return // UDP only
        if (p.size < ihl + 8) return
        if (u16(p, ihl + 2) != 53) return // DNS only
        val udpLen = u16(p, ihl + 4)
        val dnsStart = ihl + 8
        val dnsEnd = minOf(p.size, ihl + udpLen)
        if (dnsEnd - dnsStart < 12) return
        val dns = p.copyOfRange(dnsStart, dnsEnd)

        val q = parseQuestion(dns)
        if (q != null && BlockList.isBlocked(this, q.first)) {
            // Reply NXDOMAIN ("this site does not exist")
            val resp = dns.copyOfRange(0, q.second)
            resp[2] = 0x81.toByte()
            resp[3] = 0x83.toByte()
            for (i in 6..11) resp[i] = 0
            writeReply(p, ihl, resp, out)
        } else {
            pool.execute { forward(p, ihl, dns, out) }
        }
    }

    private fun forward(orig: ByteArray, ihl: Int, dns: ByteArray, out: FileOutputStream) {
        try {
            val sock = DatagramSocket()
            try {
                protect(sock) // keep this socket outside the VPN so it doesn't loop
                sock.soTimeout = 5000
                val addr = InetAddress.getByAddress(UPSTREAM_DNS)
                sock.send(DatagramPacket(dns, dns.size, addr, 53))
                val rb = ByteArray(4096)
                val rp = DatagramPacket(rb, rb.size)
                sock.receive(rp)
                writeReply(orig, ihl, rb.copyOf(rp.length), out)
            } finally {
                sock.close()
            }
        } catch (e: Exception) {
            // timeout or network error: the app will simply retry
        }
    }

    /** Returns (domain name, end offset of the question section) or null. */
    private fun parseQuestion(d: ByteArray): Pair<String, Int>? {
        if (u16(d, 4) < 1) return null
        var i = 12
        val sb = StringBuilder()
        while (true) {
            if (i >= d.size) return null
            val len = d[i].toInt() and 0xFF
            if (len == 0) {
                i++
                break
            }
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(d, i + 1, len, Charsets.ISO_8859_1))
            i += 1 + len
        }
        if (i + 4 > d.size) return null
        return Pair(sb.toString().lowercase(), i + 4)
    }

    /** Wraps a DNS payload in UDP + IPv4 headers, addressed back to the asker. */
    private fun writeReply(req: ByteArray, ihl: Int, payload: ByteArray, out: FileOutputStream) {
        val total = 20 + 8 + payload.size
        val r = ByteArray(total)
        r[0] = 0x45
        putU16(r, 2, total)
        putU16(r, 6, 0x4000)
        r[8] = 64
        r[9] = 17
        System.arraycopy(req, 16, r, 12, 4) // source = original destination
        System.arraycopy(req, 12, r, 16, 4) // destination = original source
        putU16(r, 10, checksum(r, 0, 20))
        System.arraycopy(req, ihl + 2, r, 20, 2) // source port = 53
        System.arraycopy(req, ihl, r, 22, 2) // destination port = asker's port
        putU16(r, 24, 8 + payload.size)
        // UDP checksum left at 0 (allowed for IPv4)
        System.arraycopy(payload, 0, r, 28, payload.size)
        synchronized(writeLock) { out.write(r) }
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun putU16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v shr 8).toByte()
        b[o + 1] = v.toByte()
    }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len) {
            sum += u16(b, i)
            i += 2
        }
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun stopVpn() {
        running = false
        try {
            tun?.close()
        } catch (e: Exception) {
        }
        tun = null
    }

    override fun onRevoke() {
        BlockList.setEnabled(this, false)
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        pool.shutdownNow()
        super.onDestroy()
    }
}
