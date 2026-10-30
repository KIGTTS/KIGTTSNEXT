package com.kigtts.next.sharing

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.*
import android.util.Base64
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** BLE exchanges a session descriptor and tiny avatar; shared media travels over local Wi-Fi. */
@SuppressLint("MissingPermission")
internal class FaceBluetoothDiscovery(private val context: Context, private val descriptor: () -> JSONObject,
    private val found: (FacePeer) -> Unit, private val avatar: () -> ByteArray? = { null }) {
    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val adapter get() = manager?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private var server: BluetoothGattServer? = null
    private var advertising: AdvertiseCallback? = null
    private var scanning: ScanCallback? = null
    private val connections = ConcurrentHashMap.newKeySet<BluetoothGatt>()
    private val lastSeen = ConcurrentHashMap<String, Long>()
    private val mtu = ConcurrentHashMap<String, Int>()
    private var generation = 0
    fun start(receiving: Boolean) {
        stop(); val epoch = generation
        val bluetooth = adapter?.takeIf { it.isEnabled } ?: return
        if (receiving && bluetooth.isMultipleAdvertisementSupported) runCatching {
            server = manager.openGattServer(context, object : BluetoothGattServerCallback() {
                override fun onMtuChanged(device: BluetoothDevice, value: Int) { mtu[device.address] = value }
                override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
                    val bytes = when (characteristic.uuid) {
                        CHARACTERISTIC -> descriptor().toString().toByteArray(Charsets.UTF_8)
                        AVATAR -> avatar()?.takeIf { it.size <= 480 } ?: byteArrayOf()
                        else -> return
                    }
                    if (offset > bytes.size) server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                    else server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        bytes.copyOfRange(offset, minOf(bytes.size, offset + (mtu[device.address] ?: 23) - 1)))
                }
            })
            val service = BluetoothGattService(SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            service.addCharacteristic(BluetoothGattCharacteristic(CHARACTERISTIC, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            service.addCharacteristic(BluetoothGattCharacteristic(AVATAR, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ))
            server?.addService(service)
            val callback = object : AdvertiseCallback() { }
            advertising = callback
            bluetooth.bluetoothLeAdvertiser?.startAdvertising(AdvertiseSettings.Builder().setConnectable(true)
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setTimeout(0).build(),
                AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE)).setIncludeDeviceName(false).build(), callback)
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (generation != epoch || connections.size >= 3) return
                val now = SystemClock.elapsedRealtime()
                if (lastSeen[result.device.address]?.let { now - it < 12_000 } == true) return
                lastSeen[result.device.address] = now
                connect(result, epoch)
            }
        }
        scanning = callback
        runCatching { bluetooth.bluetoothLeScanner?.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback) }
    }
    private fun connect(result: ScanResult, epoch: Int) {
        var connection: BluetoothGatt? = null
        var peer: FacePeer? = null
        fun finish() { connection?.let { connections -= it; runCatching { it.disconnect(); it.close() } } }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (generation != epoch || status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) { finish(); return }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!gatt.requestMtu(512)) gatt.discoverServices()
                }
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { if (generation == epoch) gatt.discoverServices() else finish() }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val characteristic = gatt.getService(SERVICE)?.getCharacteristic(CHARACTERISTIC)
                if (generation != epoch || status != BluetoothGatt.GATT_SUCCESS || characteristic == null || !gatt.readCharacteristic(characteristic)) finish()
            }
            @Deprecated("Android 12 callback")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                @Suppress("DEPRECATION") read(gatt, characteristic, characteristic.value, status)
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) { read(gatt, characteristic, value, status) }
            private fun read(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray?, status: Int) {
                var pendingAvatar = false
                try {
                    if (generation != epoch || status != BluetoothGatt.GATT_SUCCESS || bytes == null || bytes.size > 2048) return
                    if (characteristic.uuid == CHARACTERISTIC) {
                        peer = faceBluetoothPeer(JSONObject(String(bytes, Charsets.UTF_8)), result.device.address, result.rssi)
                        found(requireNotNull(peer))
                        gatt.getService(SERVICE)?.getCharacteristic(AVATAR)?.let { pendingAvatar = gatt.readCharacteristic(it) }
                    } else if (characteristic.uuid == AVATAR && bytes.size in 1..480) {
                        peer?.copy(avatar = Base64.encodeToString(bytes, Base64.NO_WRAP))?.let(found)
                    }
                } catch (_: Exception) { } finally { if (!pendingAvatar) finish() }
            }
        }
        runCatching { connection = result.device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE); connection?.let { connections += it } }
        handler.postDelayed({ finish() }, 12_000)
    }
    fun stop() {
        generation++
        scanning?.let { callback -> runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) } }; scanning = null
        advertising?.let { callback -> runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) } }; advertising = null
        connections.toList().forEach { runCatching { it.disconnect(); it.close() } }; connections.clear()
        server?.close(); server = null; lastSeen.clear(); mtu.clear()
        handler.removeCallbacksAndMessages(null)
    }
    companion object {
        val SERVICE: UUID = UUID.fromString("431db230-f93c-4f40-a736-9db1c6657915")
        val CHARACTERISTIC: UUID = UUID.fromString("431db231-f93c-4f40-a736-9db1c6657915")
        val AVATAR: UUID = UUID.fromString("431db232-f93c-4f40-a736-9db1c6657915")
    }
}
