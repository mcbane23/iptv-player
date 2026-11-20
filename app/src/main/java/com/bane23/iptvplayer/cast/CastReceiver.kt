package com.bane23.iptvplayer.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.bane23.iptvplayer.data.cast.CastCommand
import com.bane23.iptvplayer.data.cast.CastHandler
import com.bane23.iptvplayer.data.cast.CastInfo
import com.bane23.iptvplayer.data.cast.CastPairing
import com.bane23.iptvplayer.data.cast.CastPlayRequest
import com.bane23.iptvplayer.data.cast.CastServer
import com.bane23.iptvplayer.data.cast.CastStatus
import com.bane23.iptvplayer.data.lan.TransferBundle
import com.bane23.iptvplayer.data.lan.TransferResult
import com.bane23.iptvplayer.data.prefs.SettingsStore
import com.bane23.iptvplayer.data.repo.TransferRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/** Jedan zahtev za puštanje sa telefona; [id] raste, da se i isti sadržaj pusti ponovo. */
data class CastSession(val id: Int, val request: CastPlayRequest)

/** Prenos sa telefona koji je u toku ([result] je null) ili upravo završen. */
data class IncomingTransfer(val step: Int = 0, val total: Int = 0, val result: TransferResult? = null)

/**
 * Prijem na TV-u: dok je aplikacija otvorena, telefon sa istom aplikacijom u kućnoj mreži može da
 * pusti kanal ili film na ovom TV-u i da njime upravlja. TV se oglašava u mreži, a pri prvom
 * povezivanju prikazuje kod koji se prekucava na telefonu.
 */
@Singleton
class CastReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
    private val transfers: TransferRepository,
) : CastHandler {
    private val pairing = CastPairing()
    private val server = CastServer(this)
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "cast-receiver").apply { isDaemon = true } }
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var cachedKey: String? = null
    private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loading: Job? = null
    private var nextId = 0

    private val _pairingCode = MutableStateFlow<String?>(null)

    /** Kod koji treba prikazati na ekranu, ili null. */
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _session = MutableStateFlow<CastSession?>(null)
    val session: StateFlow<CastSession?> = _session.asStateFlow()

    private val _commands = MutableSharedFlow<CastCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<CastCommand> = _commands.asSharedFlow()

    private val _transfer = MutableStateFlow<IncomingTransfer?>(null)

    /** Prenos podešavanja sa telefona koji treba prikazati na ekranu, ili null. */
    val transfer: StateFlow<IncomingTransfer?> = _transfer.asStateFlow()

    fun dismissTransfer() {
        if (_transfer.value?.result != null) _transfer.value = null
    }

    /** Stanje puštanja; osvežava ga plejer, a čita telefon. */
    val status = MutableStateFlow(CastStatus())

    private val deviceName: String by lazy {
        val named = try {
            Settings.Global.getString(context.contentResolver, "device_name")
        } catch (_: RuntimeException) {
            null
        }
        named?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "TV"
    }

    /** Pokreće prijem (aplikacija je postala vidljiva). */
    fun start() = worker.execute {
        try {
            val port = server.start()
            register(port)
        } catch (_: IOException) {
            // Nema mreže; probaće se pri sledećem otvaranju aplikacije.
        } catch (_: RuntimeException) {
            // Oglašavanje u mreži nije uspelo.
        }
    }

    /** Zaustavlja prijem (aplikacija više nije vidljiva). */
    fun stop() = worker.execute {
        unregister()
        server.stop()
        pairing.cancel()
        _pairingCode.value = null
    }

    fun dismissPairing() {
        pairing.cancel()
        _pairingCode.value = null
    }

    /** Plejer je zatvoren na TV-u ili je telefon zaustavio puštanje. */
    fun closeSession() {
        _session.value = null
        status.value = CastStatus()
    }

    override fun info() = CastInfo(name = deviceName)

    override fun startPairing() {
        _pairingCode.value = pairing.begin()
    }

    override fun confirmPairing(code: String): String? {
        if (!pairing.confirm(code)) {
            if (pairing.current() == null) _pairingCode.value = null
            return null
        }
        _pairingCode.value = null
        return key()
    }

    override fun isKeyValid(key: String): Boolean =
        MessageDigest.isEqual(key.toByteArray(Charsets.UTF_8), key().toByteArray(Charsets.UTF_8))

    override fun play(request: CastPlayRequest) {
        val id = synchronized(this) { ++nextId }
        status.value = CastStatus(
            active = true,
            title = request.title,
            live = request.live,
            buffering = true,
            positionMs = request.positionMs,
        )
        _session.value = CastSession(id, request)
    }

    override fun command(command: CastCommand) {
        if (command.action == CastCommand.STOP) closeSession() else _commands.tryEmit(command)
    }

    override fun status(): CastStatus = status.value

    /**
     * Telefonu se odgovara čim je paket primljen i primenjeno sve što ne traži preuzimanje. Nove
     * liste TV zatim učitava sam, u pozadini, a napredak i ishod pokazuje na svom ekranu. Jedan po
     * jedan: drugi paket čeka da se prvi učita do kraja.
     */
    @Synchronized
    override fun transfer(bundle: TransferBundle): TransferResult {
        runBlocking { loading?.join() }
        _transfer.value = IncomingTransfer()
        val pending = try {
            runBlocking { transfers.beginImport(bundle) }
        } catch (e: Exception) {
            _transfer.value = null
            throw e
        }
        if (pending.queued == 0) {
            _transfer.value = IncomingTransfer(result = pending.result)
        } else {
            _transfer.value = IncomingTransfer(step = 1, total = pending.queued)
            loading = importScope.launch {
                val final = try {
                    pending.finish { step, total -> _transfer.value = IncomingTransfer(step, total) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Ishod nije poznat; poruka se sklanja, a ono što je učitano ostaje.
                    null
                }
                _transfer.value = final?.let { IncomingTransfer(result = it) }
            }
        }
        return pending.result
    }

    /** Ključ ovog TV-a; pravi se jednom i čuva u podešavanjima. */
    @Synchronized
    private fun key(): String {
        cachedKey?.let { return it }
        val stored = runBlocking {
            settings.castKey.first() ?: newKey().also { settings.setCastKey(it) }
        }
        cachedKey = stored
        return stored
    }

    private fun register(port: Int) {
        val manager = nsd ?: return
        if (registration != null) return
        val info = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = CastServer.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun unregister() {
        val listener = registration ?: return
        registration = null
        try {
            nsd?.unregisterService(listener)
        } catch (_: RuntimeException) {
            // već odjavljen
        }
    }

    private companion object {
        const val KEY_LENGTH = 32
        const val KEY_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        fun newKey(): String {
            val random = SecureRandom()
            return (1..KEY_LENGTH).map { KEY_ALPHABET[random.nextInt(KEY_ALPHABET.length)] }.joinToString("")
        }
    }
}
