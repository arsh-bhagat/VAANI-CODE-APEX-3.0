package com.bithead.shelter.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class MeshPhase { STOPPED, STARTING, ACTIVE }
enum class MeshProblem { PERMISSION, BLUETOOTH_OFF, UNSUPPORTED, LOCATION_OFF, ADVERTISE, SCAN, STARTUP }
data class MeshState(val phase: MeshPhase = MeshPhase.STOPPED, val peers: List<MeshPeer> = emptyList(),
    val problem: MeshProblem? = null)

/** Direct BLE discovery/profile exchange. Not Bluetooth SIG Mesh and not a multi-hop relay. */
@SuppressLint("MissingPermission") // Every entry point checks grants; revocation is caught at the boundary.
class BluetoothMeshController(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(MeshState())
    val state = mutableState.asStateFlow()
    private var epoch = 0L
    private var server: BluetoothGattServer? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var gatt: BluetoothGatt? = null
    private var sessionId = ""
    private var owner = MeshOwnerProfile()
    private var location: MeshLocation? = null
    private val roster = MeshRoster()
    private val devices = linkedMapOf<String, ScanResult>()
    private val lastSeen = mutableMapOf<String, Long>()
    private val lastQuery = mutableMapOf<String, Long>()
    private val identities = mutableMapOf<String, String>()
    private val queue = linkedSetOf<String>()
    private val readSnapshots = mutableMapOf<String, ByteArray>()
    private val mtus = mutableMapOf<String, Int>()

    fun hasBleHardware(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && manager?.adapter != null
    fun hasPermissions(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    fun bluetoothEnabled(): Boolean = runCatching { hasPermissions() && manager?.adapter?.isEnabled == true }.getOrDefault(false)
    fun updateLocation(value: MeshLocation?) { location = value }

    fun start(profile: MeshOwnerProfile) {
        stop()
        if (!hasPermissions()) { fail(MeshProblem.PERMISSION); return }
        if (!MeshCodec.validName(profile.displayName) || !MeshCodec.validPhone(profile.phone) || !MeshCodec.validEmail(profile.email)) { fail(MeshProblem.STARTUP); return }
        try {
            val adapter = manager?.adapter ?: return fail(MeshProblem.UNSUPPORTED)
            if (!adapter.isEnabled) return fail(MeshProblem.BLUETOOTH_OFF)
            if (!adapter.isMultipleAdvertisementSupported) return fail(MeshProblem.UNSUPPORTED)
            val lm = context.getSystemService(LocationManager::class.java)
            val locationEnabled = if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else
                android.provider.Settings.Secure.getInt(context.contentResolver, android.provider.Settings.Secure.LOCATION_MODE, 0) != 0
            if (!locationEnabled) return fail(MeshProblem.LOCATION_OFF)
            owner = profile
            sessionId = UUID.randomUUID().toString()
            mutableState.value = MeshState(MeshPhase.STARTING)
            val generation = epoch
            scanner = adapter.bluetoothLeScanner ?: return fail(MeshProblem.UNSUPPORTED)
            advertiser = adapter.bluetoothLeAdvertiser ?: return fail(MeshProblem.UNSUPPORTED)
            val callback = object : BluetoothGattServerCallback() {
                override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                    post {
                        if (generation != epoch) return@post
                        if (status != BluetoothGatt.GATT_SUCCESS) fail(MeshProblem.STARTUP)
                        else beginAdvertising(generation)
                    }
                }
                override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                    post { if (generation == epoch) mtus[device.address] = mtu.coerceIn(23, 517) }
                }
                override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                    post {
                        if (generation == epoch && newState == BluetoothProfile.STATE_DISCONNECTED) {
                            readSnapshots.remove(device.address); mtus.remove(device.address)
                        }
                    }
                }
                override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int,
                    characteristic: BluetoothGattCharacteristic) {
                    post {
                        if (generation != epoch) return@post
                        val currentServer = server ?: return@post
                        if (characteristic.uuid != PROFILE_UUID) {
                            currentServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                            return@post
                        }
                        // Freeze one payload per long read so location updates cannot corrupt chunk boundaries.
                        if (offset == 0) {
                            if (readSnapshots.size >= 32) readSnapshots.clear()
                            readSnapshots[device.address] = MeshCodec.encode(
                                owner.publicProfile(sessionId, location, System.currentTimeMillis()))
                        }
                        val bytes = readSnapshots[device.address]
                        if (bytes == null || offset !in 0..bytes.size) {
                            currentServer.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                        } else {
                            val end = minOf(bytes.size, offset + (mtus[device.address] ?: 23) - 1)
                            currentServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.copyOfRange(offset, end))
                        }
                    }
                }
            }
            server = manager.openGattServer(context, callback) ?: return fail(MeshProblem.UNSUPPORTED)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY).apply {
                addCharacteristic(BluetoothGattCharacteristic(PROFILE_UUID, BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ))
            }
            if (server?.addService(service) != true) return fail(MeshProblem.STARTUP)
            handler.postDelayed({ if (generation == epoch && mutableState.value.phase == MeshPhase.STARTING) fail(MeshProblem.STARTUP) }, 10_000)
        } catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
        catch (_: Exception) { fail(MeshProblem.STARTUP) }
    }

    private fun post(action: () -> Unit) {
        handler.post {
            try { action() }
            catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
            catch (_: Exception) { fail(MeshProblem.STARTUP) }
        }
    }

    private fun beginAdvertising(generation: Long) {
        try {
            advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settings: AdvertiseSettings) {
                    post { if (generation == epoch) beginScan(generation) }
                }
                override fun onStartFailure(errorCode: Int) {
                    post { if (generation == epoch) fail(MeshProblem.ADVERTISE) }
                }
            }
            advertiser?.startAdvertising(AdvertiseSettings.Builder().setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build(),
                AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).setIncludeDeviceName(false).build(), advertiseCallback)
        } catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
        catch (_: Exception) { fail(MeshProblem.ADVERTISE) }
    }

    private fun beginScan(generation: Long) {
        try {
            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    post { if (generation == epoch && mutableState.value.phase == MeshPhase.ACTIVE) onSeen(result) }
                }
                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach { onScanResult(0, it) }
                }
                override fun onScanFailed(errorCode: Int) {
                    post { if (generation == epoch) fail(MeshProblem.SCAN) }
                }
            }
            scanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build(), scanCallback)
            mutableState.value = MeshState(MeshPhase.ACTIVE)
            tick(generation)
        } catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
        catch (_: Exception) { fail(MeshProblem.SCAN) }
    }

    private fun onSeen(result: ScanResult) {
        try {
            val address = result.device.address
            val now = SystemClock.elapsedRealtime()
            if (address !in devices && devices.size >= 40) return
            devices[address] = result
            lastSeen[address] = now
            identities[address]?.let { roster.seen(it, result.rssi, now) }
            if (now - (lastQuery[address] ?: Long.MIN_VALUE / 2) >= 10_000L) queue.add(address)
            publish()
            drain()
        } catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
    }

    private fun tick(generation: Long) {
        if (generation != epoch || mutableState.value.phase != MeshPhase.ACTIVE) return
        if (!hasPermissions()) return fail(MeshProblem.PERMISSION)
        if (!bluetoothEnabled()) return fail(MeshProblem.BLUETOOTH_OFF)
        val now = SystemClock.elapsedRealtime()
        val expired = lastSeen.filterValues { now - it > 30_000L }.keys.toList()
        expired.forEach { devices.remove(it); lastSeen.remove(it); lastQuery.remove(it); identities.remove(it); queue.remove(it) }
        publish()
        handler.postDelayed({ tick(generation) }, 2_000L)
    }

    private fun publish() { mutableState.value = mutableState.value.copy(peers = roster.snapshot(SystemClock.elapsedRealtime())) }

    private fun drain() {
        if (gatt != null || queue.isEmpty() || mutableState.value.phase != MeshPhase.ACTIVE) return
        val address = queue.first().also { queue.remove(it) }
        val result = devices[address] ?: return drain()
        val generation = epoch
        lastQuery[address] = SystemClock.elapsedRealtime()
        var finished = false
        var readStarted = false
        lateinit var timeout: Runnable
        fun finish(connection: BluetoothGatt?) {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            lastQuery[address] = SystemClock.elapsedRealtime()
            if (gatt === connection) gatt = null
            runCatching { connection?.disconnect() }; runCatching { connection?.close() }
            if (generation == epoch) { publish(); drain() }
        }
        fun read(connection: BluetoothGatt) {
            if (finished || generation != epoch || readStarted) return
            readStarted = true
            val characteristic = connection.getService(SERVICE_UUID)?.getCharacteristic(PROFILE_UUID)
            if (characteristic == null || !connection.readCharacteristic(characteristic)) finish(connection)
        }
        fun received(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (finished || generation != epoch || gatt !== connection) return
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == PROFILE_UUID) {
                MeshCodec.decode(value, System.currentTimeMillis())?.takeIf { it.sessionId != sessionId }?.let {
                    identities[address]?.takeIf { previous -> previous != it.sessionId }?.let(roster::remove)
                    identities[address] = it.sessionId
                    roster.update(it, result.rssi, lastSeen[address] ?: SystemClock.elapsedRealtime())
                }
            }
            finish(connection)
        }
        timeout = Runnable { finish(gatt) }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
                post {
                    if (finished || generation != epoch || gatt !== connection) { runCatching { connection.close() }; return@post }
                    if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                        if (!connection.discoverServices()) finish(connection)
                    } else finish(connection)
                }
            }
            override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
                post {
                    if (finished || generation != epoch || gatt !== connection) return@post
                    if (status != BluetoothGatt.GATT_SUCCESS) finish(connection)
                    else if (!connection.requestMtu(517)) read(connection)
                }
            }
            override fun onMtuChanged(connection: BluetoothGatt, mtu: Int, status: Int) {
                post { if (gatt === connection && generation == epoch) read(connection) }
            }
            @Deprecated("Legacy callback for API 26–32")
            override fun onCharacteristicRead(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                val value = characteristic.value?.clone() ?: byteArrayOf()
                post { received(connection, characteristic, value, status) }
            }
            override fun onCharacteristicRead(connection: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                val copy = value.clone()
                post { received(connection, characteristic, copy, status) }
            }
        }
        try {
            gatt = result.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) finish(null) else handler.postDelayed(timeout, 8_000L)
        } catch (_: SecurityException) { fail(MeshProblem.PERMISSION) }
        catch (_: Exception) { finish(gatt) }
    }

    private fun fail(problem: MeshProblem) { stop(); mutableState.value = MeshState(problem = problem) }

    fun stop() {
        epoch++
        handler.removeCallbacksAndMessages(null)
        runCatching { scanCallback?.let { scanner?.stopScan(it) } }
        runCatching { advertiseCallback?.let { advertiser?.stopAdvertising(it) } }
        runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }; gatt = null
        runCatching { server?.close() }; server = null
        scanner = null; advertiser = null; scanCallback = null; advertiseCallback = null
        roster.clear(); devices.clear(); lastSeen.clear(); lastQuery.clear(); identities.clear(); queue.clear()
        readSnapshots.clear(); mtus.clear(); sessionId = ""; owner = MeshOwnerProfile()
        mutableState.value = MeshState()
    }

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("7745f3f0-8db8-4ad4-bdb6-51606c509f01")
        val PROFILE_UUID: UUID = UUID.fromString("7745f3f1-8db8-4ad4-bdb6-51606c509f01")
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
