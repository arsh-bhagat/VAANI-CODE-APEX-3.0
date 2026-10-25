package com.bithead.shelter.mesh

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MeshModelsTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val now = 1_000_000L
    private val point = MeshLocation(28.612345, 77.212345, now, 20f)

    @Test fun publicProjectionRequiresSeparateContactAndLocationConsent() {
        val owner = MeshOwnerProfile("Asha", "+91 12345", false, false, email = "asha@example.com")
        val public = owner.publicProfile(id, point, now)
        assertNull(public.phone); assertNull(public.location); assertNull(public.email)
        val shared = owner.copy(sharePhone = true, shareLocation = true, shareEmail = true).publicProfile(id, point, now)
        assertEquals("+91 12345", shared.phone)
        assertEquals("asha@example.com", shared.email)
        assertEquals(28.612, shared.location!!.latitude, .000001)
        assertTrue(shared.location!!.accuracyMeters >= 150f)
    }

    @Test fun staleOrInvalidLocationsAreNeverExposed() {
        val owner = MeshOwnerProfile("Asha", shareLocation = true)
        assertNull(owner.publicProfile(id, point.copy(timestampMillis = now - 120_001), now).location)
        assertNull(owner.publicProfile(id, point.copy(latitude = Double.NaN), now).location)
        assertNull(owner.publicProfile(id, point.copy(timestampMillis = now + 30_001), now).location)
    }

    @Test fun versionedPacketRoundTripsUnicodeAndOptionalFields() {
        val profile = MeshPublicProfile(id, "आशा / Asha", "+91 12345", point.approximate(), "asha@example.com")
        val packet = MeshCodec.encode(profile)
        assertTrue(packet.size <= MeshCodec.MAX_BYTES)
        assertEquals(profile, MeshCodec.decode(packet, now))
        val minimal = profile.copy(phone = null, location = null, email = null)
        assertEquals(minimal, MeshCodec.decode(MeshCodec.encode(minimal), now))
    }

    @Test fun receiverDropsStaleLocationButKeepsConsentedProfile() {
        val profile = MeshPublicProfile(id, "Asha", null, point)
        assertNull(MeshCodec.decode(MeshCodec.encode(profile), now + 120_001)!!.location)
    }

    @Test fun malformedTruncatedOversizedAndUnknownVersionsAreRejected() {
        val bytes = MeshCodec.encode(MeshPublicProfile(id, "Asha", null, null))
        assertNull(MeshCodec.decode(bytes.copyOf(bytes.size - 1), now))
        assertNull(MeshCodec.decode(ByteArray(513), now))
        assertNull(MeshCodec.decode(byteArrayOf(), now))
        assertNull(MeshCodec.decode(bytes.clone().apply { this[4] = 99 }, now))
        assertNull(MeshCodec.decode(bytes + byteArrayOf(0), now))
    }

    @Test fun unsafeNamesAndPhoneUrisCannotEnterTheProtocol() {
        assertFalse(MeshCodec.validName(" \n"))
        assertFalse(MeshCodec.validName("Asha\nadmin"))
        assertFalse(MeshCodec.validName("x".repeat(49)))
        assertFalse(MeshCodec.validPhone("123;intent://example"))
        assertFalse(MeshCodec.validPhone("1".repeat(33)))
        assertFalse(MeshCodec.validEmail("asha@example.com?body=bad"))
        assertTrue(MeshCodec.validEmail("asha@example.com"))
    }

    @Test fun rosterRefreshesDeduplicatesExpiresAndCapsPeers() {
        val roster = MeshRoster(ttlMs = 30_000, maxPeers = 2)
        val one = MeshPublicProfile(id, "One", null, null)
        roster.update(one, -40, 0)
        roster.update(one.copy(displayName = "Updated"), -30, 1_000)
        assertEquals(1, roster.snapshot(1_000).size)
        roster.seen(id, -20, 20_000)
        assertEquals("Updated", roster.snapshot(40_000).single().profile.displayName)
        assertTrue(roster.snapshot(50_001).isEmpty())
        repeat(3) { roster.update(one.copy(sessionId = UUID.randomUUID().toString()), -40, it.toLong()) }
        assertEquals(2, roster.snapshot(3).size)
        roster.clear()
        assertTrue(roster.snapshot(3).isEmpty())
    }
    @Test fun signalUpdatesDoNotReorderRowsOrRetainRemovedSessions() {
        val roster = MeshRoster()
        val one = MeshPublicProfile(id, "Asha", null, null)
        val two = MeshPublicProfile("22345678-1234-1234-1234-123456789abc", "Zoya", null, null)
        roster.update(two, -40, 100)
        roster.update(one, -30, 200)
        val order = roster.snapshot(200).map { it.profile.sessionId }
        roster.seen(two.sessionId, -20, 300)
        assertEquals(order, roster.snapshot(300).map { it.profile.sessionId })
        roster.remove(one.sessionId)
        assertEquals(two.sessionId, roster.snapshot(300).single().profile.sessionId)
    }

}
