package com.adblocker.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

data class FilterList(
    val id: String,
    val name: String,
    val description: String,
    val url: String
)

object FilterCatalog {
    const val PREFS = "adblocker"
    const val KEY_LISTS = "lists"
    const val KEY_DARK = "dark"
    const val KEY_AUTO = "autostart"

    /** A protection level the user picks from (list names stay hidden from the user). */
    data class Level(val name: String, val ids: Set<String>, val hint: String)

    val levels = listOf(
        Level("Basic", setOf("stevenblack", "peterlowe"), "Blocks the most common ads, light on the device"),
        Level(
            "Balanced",
            setOf("oisd", "stevenblack", "adguard", "peterlowe"),
            "Best balance between protection and site compatibility"
        ),
        Level(
            "Strong",
            setOf("hagezi", "oisd", "stevenblack", "peterlowe", "adguard", "easylist"),
            "Maximum protection against ads and trackers"
        )
    )

    val defaults = setOf("oisd", "stevenblack", "adguard", "peterlowe")

    val all = listOf(
        FilterList(
            "hagezi", "HaGeZi Ultimate", "Strong blocking of ads, tracking and malicious domains",
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/ultimate.txt"
        ),
        FilterList(
            "oisd", "OISD Big", "Large, balanced list that rarely breaks sites",
            "https://big.oisd.nl/domainswild2"
        ),
        FilterList(
            "stevenblack", "StevenBlack Hosts", "Ads, tracking and malware",
            "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        ),
        FilterList(
            "peterlowe", "Peter Lowe", "Ad and tracking servers",
            "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
        ),
        FilterList(
            "adguard", "AdGuard DNS", "Mobile ads and tracking",
            "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt"
        ),
        FilterList(
            "easylist", "EasyList", "Web ads (domain rules only)",
            "https://easylist.to/easylist/easylist.txt"
        )
    )

    fun selectedIds(ctx: Context): Set<String> =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_LISTS, null)?.toSet() ?: defaults
}

/** Turns one line of a list (hosts, ABP or plain domain format) into a domain, or null. */
object DomainParser {
    private val validHost = Regex("^[a-z0-9_-]+(\\.[a-z0-9_-]+)+$")
    private val whitespace = Regex("\\s+")
    private val badChars = charArrayOf('/', '^', '*', '?', '=', '&', ':', '$', '|', '@', ',', '%', '~')

    fun parse(raw: String): String? {
        var line = raw.trim()
        if (line.isEmpty()) return null
        val first = line[0]
        if (first == '#' || first == '!' || first == '[') return null
        if (line.startsWith("@@")) return null
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#\$#")) return null

        if (line.startsWith("||")) {
            line = line.substring(2)
            if (line.endsWith("^")) line = line.dropLast(1)
            if (line.any { it in badChars }) return null
            return validHost.matchEntire(line.lowercase())?.value
        }

        val hash = line.indexOf('#')
        if (hash >= 0) line = line.substring(0, hash).trim()
        val parts = line.split(whitespace)
        var host = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) parts[1] else parts[0]
        host = host.lowercase().removePrefix("*.").removePrefix(".").trimEnd('.')
        if (host == "localhost" || host == "localhost.localdomain" || host == "0.0.0.0") return null
        return if (validHost.matches(host)) host else null
    }
}

/**
 * Blocked-domain store: a sorted array of 64-bit hashes, one per domain.
 * Lookups are a binary search, and loading the cache is much faster than storing strings.
 */
object DomainStore {
    @Volatile
    private var table: LongArray = LongArray(0)
    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()

    fun hash(s: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV-1a offset basis
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    fun replace(set: Set<String>) {
        val arr = LongArray(set.size)
        var i = 0
        for (d in set) arr[i++] = hash(d)
        replaceHashes(arr)
    }

    /** Sorts [arr] in place and makes it the active table. */
    fun replaceHashes(arr: LongArray) {
        arr.sort()
        table = arr
        _size.value = arr.size
    }

    fun snapshot(): LongArray = table

    /** Checks the domain and every parent domain (ads.x.com -> x.com -> com). */
    fun isBlocked(name: String): Boolean {
        val t = table
        if (t.isEmpty()) return false
        var d = name
        while (true) {
            if (t.binarySearch(hash(d)) >= 0) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }
}

object BlockStats {
    private val _blocked = MutableStateFlow(0L)
    val blocked: StateFlow<Long> = _blocked.asStateFlow()

    private val _allowed = MutableStateFlow(0L)
    val allowed: StateFlow<Long> = _allowed.asStateFlow()

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing.asStateFlow()

    private val _status = MutableStateFlow("Protection is off")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setPreparing(v: Boolean) {
        _preparing.value = v
    }

    fun onBlocked(domain: String) {
        _blocked.update { it + 1 }
        _recent.update { (listOf(domain) + it).take(50) }
    }

    fun onAllowed() {
        _allowed.update { it + 1 }
    }

    fun setRunning(v: Boolean) {
        _running.value = v
    }

    fun setStatus(s: String) {
        _status.value = s
    }

    fun reset() {
        _blocked.value = 0
        _allowed.value = 0
        _recent.value = emptyList()
    }
}

object ListLoader {
    private const val TAG = "AdBlocker"
    private const val CACHE_VERSION = 2

    /** Lists bundled inside the app (assets/lists). OISD is not bundled, so it is downloaded. */
    private val bundledAssets = mapOf(
        "hagezi" to "lists/hagezi_ultimate.txt",
        "stevenblack" to "lists/stevenblack.txt",
        "peterlowe" to "lists/peterlowe.txt",
        "adguard" to "lists/adguard.txt",
        "easylist" to "lists/easylist.txt"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * First run: parses the lists and saves the result to a cache file.
     * Later runs: read the cache directly.
     */
    fun loadBundled(ctx: Context, lists: List<FilterList>, onProgress: (String) -> Unit): Int {
        val cache = cacheFile(ctx, lists)
        if (cache.exists()) {
            try {
                val started = System.currentTimeMillis()
                val arr = readCache(cache)
                if (arr.isNotEmpty()) {
                    DomainStore.replaceHashes(arr)
                    Log.i(TAG, "Loaded from cache: ${arr.size} domains in ${System.currentTimeMillis() - started}ms")
                    return arr.size
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cache is corrupt, rebuilding it: ${e.message}")
                cache.delete()
            }
        }
        return loadAll(ctx, lists, onProgress)
    }

    /** Manual update from the internet for all selected lists. */
    fun download(lists: List<FilterList>, onProgress: (String) -> Unit): Int =
        loadAll(null, lists, onProgress)

    /** Deletes the cache so the next start rebuilds it from scratch. */
    fun clearCache(ctx: Context) {
        ctx.filesDir.listFiles { f -> f.name.startsWith("domains_") }?.forEach { it.delete() }
    }

    private fun loadAll(ctx: Context?, lists: List<FilterList>, onProgress: (String) -> Unit): Int {
        if (lists.isEmpty()) {
            onProgress("Pick at least one protection level")
            return 0
        }
        val started = System.currentTimeMillis()
        val set = HashSet<String>()
        val errors = mutableListOf<String>()
        var okLists = 0

        for (list in lists) {
            onProgress("Preparing protection...")
            val asset = bundledAssets[list.id]
            if (ctx != null && asset != null) {
                try {
                    val n = readAsset(ctx, asset, set)
                    okLists++
                    Log.i(TAG, "${list.name} (bundled): $n domains")
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "${list.name} bundled copy failed: ${e.message}, downloading instead")
                }
            }
            var loaded = -1
            var lastError = "unknown"
            for (attempt in 1..2) {
                try {
                    loaded = fetch(list, set)
                    break
                } catch (e: Exception) {
                    lastError = "${e.javaClass.simpleName}: ${e.message}"
                    Log.w(TAG, "${list.name} attempt $attempt failed: $lastError")
                    if (attempt < 2) Thread.sleep(1500)
                }
            }
            if (loaded >= 0) {
                okLists++
                Log.i(TAG, "${list.name}: $loaded domains")
            } else {
                errors += "${list.name} ($lastError)"
            }
        }

        if (set.isNotEmpty()) {
            DomainStore.replace(set)
            Log.i(TAG, "Total ${set.size} domains from $okLists lists in ${System.currentTimeMillis() - started}ms")
            // Save the cache even if a downloaded list failed, so the next start is fast.
            if (ctx != null) {
                try {
                    writeCache(cacheFile(ctx, lists), DomainStore.snapshot())
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save cache: ${e.message}")
                }
            }
        } else {
            Log.e(TAG, "No domains loaded: ${errors.joinToString(" | ")}")
            onProgress("Loading failed")
        }
        return set.size
    }

    private fun cacheFile(ctx: Context, lists: List<FilterList>): File {
        val key = lists.map { it.id }.sorted().joinToString("-")
        return File(ctx.filesDir, "domains_v${CACHE_VERSION}_$key.bin")
    }

    /** File layout: 4-byte count, then 8 bytes per hash. */
    private fun writeCache(file: File, arr: LongArray) {
        val buf = ByteBuffer.allocate(4 + arr.size * 8)
        buf.putInt(arr.size)
        buf.asLongBuffer().put(arr)
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { it.write(buf.array()) }
        file.delete()
        if (!tmp.renameTo(file)) throw IOException("Could not save the cache file")
    }

    private fun readCache(file: File): LongArray {
        val bytes = FileInputStream(file).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes)
        val n = buf.getInt()
        if (bytes.size != 4 + n * 8) throw IOException("Unexpected cache size")
        val arr = LongArray(n)
        buf.asLongBuffer().get(arr)
        return arr
    }

    private fun readAsset(ctx: Context, path: String, out: MutableSet<String>): Int {
        var count = 0
        ctx.assets.open(path).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                DomainParser.parse(line)?.let {
                    out.add(it)
                    count++
                }
            }
        }
        if (count == 0) throw IOException("Bundled list is empty")
        return count
    }

    /** Returns how many domains were read from the list; throws if it fails. */
    private fun fetch(list: FilterList, out: MutableSet<String>): Int {
        val request = Request.Builder()
            .url(list.url)
            .header("User-Agent", "AdBlocker/1.2")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            var count = 0
            body.charStream().buffered().useLines { lines ->
                lines.forEach { line ->
                    DomainParser.parse(line)?.let {
                        out.add(it)
                        count++
                    }
                }
            }
            if (count == 0) throw IOException("No recognizable domains in the file")
            return count
        }
    }
}
