package com.bithead.shelter.mesh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlin.math.round

data class MeshLocation(val latitude: Double, val longitude: Double, val timestampMillis: Long, val accuracyMeters: Float) {
    fun isFresh(nowMillis: Long): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            accuracyMeters.isFinite() && accuracyMeters >= 0f &&
            timestampMillis > 0 && nowMillis - timestampMillis in -30_000L..120_000L
    fun approximate() = copy(latitude = round(latitude * 1000) / 1000,
        longitude = round(longitude * 1000) / 1000, accuracyMeters = maxOf(accuracyMeters, 150f))
}

data class MeshOwnerProfile(val displayName: String = "", val phone: String = "",
    val sharePhone: Boolean = false, val shareLocation: Boolean = false,
    val email: String = "", val shareEmail: Boolean = false) {
    fun publicProfile(sessionId: String, location: MeshLocation?, nowMillis: Long) = MeshPublicProfile(
        sessionId, displayName.trim(),
        phone.trim().takeIf { sharePhone && it.isNotBlank() },
        location?.takeIf { shareLocation && it.isFresh(nowMillis) }?.approximate(),
        email.trim().takeIf { shareEmail && it.isNotBlank() }
    )
}

data class MeshPublicProfile(val sessionId: String, val displayName: String,
    val phone: String?, val location: MeshLocation?, val email: String? = null)
data class MeshPeer(val profile: MeshPublicProfile, val lastSeenElapsed: Long, val rssi: Int)

/** Only consented public fields; no Google subject IDs, ID tokens or private account objects. */
object MeshCodec {
    const val MAX_BYTES = 512
    private const val MAGIC = 0x56414E49
    private const val VERSION = 1
    fun validName(name: String) = name.trim().isNotEmpty() && name.length <= 48 && name.none(Char::isISOControl)
    fun validPhone(phone: String) = phone.length <= 32 && phone.all { it.isDigit() || it in "+ ()-." }
    fun validEmail(email: String) = email.isEmpty() || (email.length <= 120 && Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email))
    fun encode(profile: MeshPublicProfile): ByteArray {
        require(validName(profile.displayName))
        require(UUID.fromString(profile.sessionId).toString() == profile.sessionId)
        require(profile.phone == null || (profile.phone.isNotBlank() && validPhone(profile.phone)))
        require(profile.email == null || (profile.email.isNotBlank() && validEmail(profile.email)))
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeByte(VERSION)
            out.writeUTF(profile.sessionId); out.writeUTF(profile.displayName)
            out.writeUTF(profile.phone.orEmpty())
            out.writeUTF(profile.email.orEmpty())
            out.writeBoolean(profile.location != null)
            profile.location?.let {
                require(it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 &&
                    it.longitude in -180.0..180.0 && it.accuracyMeters.isFinite() && it.accuracyMeters >= 0 && it.timestampMillis > 0)
                out.writeDouble(it.latitude); out.writeDouble(it.longitude)
                out.writeLong(it.timestampMillis); out.writeFloat(it.accuracyMeters)
            }
        }
        return buffer.toByteArray().also { require(it.size <= MAX_BYTES) }
    }
    fun decode(bytes: ByteArray, nowMillis: Long): MeshPublicProfile? = runCatching {
        require(bytes.size in 1..MAX_BYTES)
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC && input.readUnsignedByte() == VERSION)
            val id = input.readUTF(); val name = input.readUTF(); val phone = input.readUTF().ifBlank { null }
            val email = input.readUTF().ifBlank { null }
            require(UUID.fromString(id).toString() == id && validName(name))
            require(phone == null || validPhone(phone))
            require(email == null || validEmail(email))
            val location = if (input.readBoolean()) MeshLocation(input.readDouble(), input.readDouble(),
                input.readLong(), input.readFloat()).takeIf { it.isFresh(nowMillis) } else null
            require(input.available() == 0)
            MeshPublicProfile(id, name, phone, location, email)
        }
    }.getOrNull()
}

/** In-memory only. No nearby-user history survives a stopped session. */
class MeshRoster(private val ttlMs: Long = 30_000L, private val maxPeers: Int = 40) {
    private val peers = linkedMapOf<String, MeshPeer>()
    fun update(profile: MeshPublicProfile, rssi: Int, nowElapsed: Long) {
        peers[profile.sessionId] = MeshPeer(profile, nowElapsed, rssi)
        while (peers.size > maxPeers) peers.remove(peers.minBy { it.value.lastSeenElapsed }.key)
    }
    fun seen(id: String, rssi: Int, nowElapsed: Long) { peers[id]?.let { peers[id] = it.copy(lastSeenElapsed = nowElapsed, rssi = rssi) } }
    fun snapshot(nowElapsed: Long): List<MeshPeer> {
        peers.entries.removeAll { nowElapsed - it.value.lastSeenElapsed > ttlMs }
        return peers.values.sortedWith(compareBy<MeshPeer> { it.profile.displayName.lowercase(java.util.Locale.ROOT) }.thenBy { it.profile.sessionId })
    }
    fun remove(id: String) { peers.remove(id) }
    fun clear() = peers.clear()
}
