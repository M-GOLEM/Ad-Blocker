package com.adblocker.app.service

/**
 * Parses IPv4/UDP/DNS packets coming from the VPN tunnel and builds the replies.
 */
object DnsPacket {

    class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dns: ByteArray,
        val domain: String
    )

    /** Returns null if the packet is not a valid DNS query (UDP port 53). */
    fun parseQuery(p: ByteArray): Query? {
        if (p.size < 28) return null
        if (((p[0].toInt() shr 4) and 0x0F) != 4) return null
        val ihl = (p[0].toInt() and 0x0F) * 4
        if (p[9].toInt() != 17) return null
        if (p.size < ihl + 8) return null
        val srcPort = u16(p, ihl)
        val dstPort = u16(p, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(p, ihl + 4)
        val start = ihl + 8
        val end = minOf(p.size, ihl + udpLen)
        if (end - start < 12) return null
        val dns = p.copyOfRange(start, end)
        if ((dns[2].toInt() and 0x80) != 0) return null // QR=1 means a response, not a query
        if (u16(dns, 4) < 1) return null               // QDCOUNT
        val domain = readName(dns, 12) ?: return null
        return Query(
            srcIp = p.copyOfRange(12, 16),
            dstIp = p.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            dns = dns,
            domain = domain
        )
    }

    /** NXDOMAIN reply ("domain does not exist") for blocked domains. */
    fun blockedReply(q: Query): ByteArray {
        val r = q.dns.copyOf()
        r[2] = 0x81.toByte() // QR=1, RD=1
        r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
        return wrap(q, r)
    }

    /** Wraps the real DNS answer that came back from the upstream server. */
    fun allowedReply(q: Query, upstreamDns: ByteArray): ByteArray = wrap(q, upstreamDns)

    private fun wrap(q: Query, payload: ByteArray): ByteArray {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[1] = 0
        putU16(p, 2, total)
        putU16(p, 4, (System.nanoTime() and 0xFFFFL).toInt())
        putU16(p, 6, 0x4000)
        p[8] = 64
        p[9] = 17
        System.arraycopy(q.dstIp, 0, p, 12, 4)
        System.arraycopy(q.srcIp, 0, p, 16, 4)
        putU16(p, 10, checksum(p, 0, 20))
        putU16(p, 20, q.dstPort) // source = 53
        putU16(p, 22, q.srcPort) // destination = client port
        putU16(p, 24, udpLen)
        putU16(p, 26, 0)
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0L
        var i = off
        while (i < off + len - 1) {
            sum += u16(b, i).toLong()
            i += 2
        }
        if (len % 2 == 1) sum += ((b[off + len - 1].toInt() and 0xFF) shl 8).toLong()
        while ((sum shr 16) != 0L) sum = (sum and 0xFFFFL) + (sum shr 16)
        return (sum.inv() and 0xFFFFL).toInt()
    }

    private fun u16(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun putU16(b: ByteArray, o: Int, v: Int) {
        b[o] = (v shr 8).toByte()
        b[o + 1] = v.toByte()
    }

    private fun readName(b: ByteArray, start: Int): String? {
        val sb = StringBuilder()
        var i = start
        while (i < b.size) {
            val len = b[i].toInt() and 0xFF
            if (len == 0) return if (sb.isEmpty()) null else sb.toString().lowercase()
            if ((len and 0xC0) != 0) return null
            if (i + 1 + len > b.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(b, i + 1, len, Charsets.US_ASCII))
            i += len + 1
        }
        return null
    }
}
