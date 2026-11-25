package com.onoseto.chipsnfish.online

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class NearTable(val code: String, val who: String)

/**
 * Finds tables of friends on the same Wi-Fi (mDNS / Network Service Discovery).
 * Everybody sitting at an online table announces it; everybody with the app open sees it.
 * The game itself still runs on the server; this only saves typing a code.
 */
object Nearby {
    private const val TYPE = "_chipsfish._tcp."
    private const val PREFIX = "CF|"
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    private var discListener: NsdManager.DiscoveryListener? = null
    private var advertised: String? = null
    private val main = Handler(Looper.getMainLooper())
    var tables by mutableStateOf<List<NearTable>>(emptyList())

    fun start(ctx: Context) {
        nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        startDiscovery()
        Online.code?.let { advertise(it, Online.myName()) }
    }

    fun stop() {
        stopDiscovery()
        unadvertise()
        tables = emptyList()
    }

    private fun parse(name: String): NearTable? {
        // "CF|ABC123|Dan" (Android may append " (2)" on name clashes)
        val n = name.replace(Regex(" \\(\\d+\\)$"), "")
        if (!n.startsWith(PREFIX)) return null
        val parts = n.removePrefix(PREFIX).split("|", limit = 2)
        val code = parts[0].uppercase().filter { it.isLetterOrDigit() }
        if (code.length < 4) return null
        return NearTable(code, parts.getOrElse(1) { "" })
    }

    private fun startDiscovery() {
        val m = nsd ?: return
        if (discListener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { discListener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                val t = parse(info.serviceName) ?: return
                main.post {
                    if (t.code == Online.code) return@post
                    tables = (tables.filter { it.code != t.code } + t).takeLast(8)
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                val t = parse(info.serviceName) ?: return
                main.post { tables = tables.filter { it.code != t.code } }
            }
        }
        discListener = l
        runCatching { m.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { discListener = null }
    }

    private fun stopDiscovery() {
        val l = discListener ?: return
        discListener = null
        runCatching { nsd?.stopServiceDiscovery(l) }
    }

    /** Announces my table on the local network. */
    fun advertise(code: String, who: String) {
        val m = nsd ?: return
        if (advertised == code) return
        unadvertise()
        val name = (PREFIX + code + "|" + who.replace("|", " ")).toByteArray().let { b ->
            if (b.size <= 60) String(b) else String(b.copyOf(60)).trimEnd('�')
        }
        val info = NsdServiceInfo().apply { serviceName = name; serviceType = TYPE; port = 47321 }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) { regListener = null; advertised = null }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        regListener = l
        advertised = code
        runCatching { m.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { regListener = null; advertised = null }
    }

    fun unadvertise() {
        val l = regListener ?: return
        regListener = null; advertised = null
        runCatching { nsd?.unregisterService(l) }
    }
}
