package com.bithead.shelter.emergency

import org.junit.Assert.*
import org.junit.Test

class EvidenceHashSmsTest {
    @Test fun fullHashAndIncidentFitOnePlainTextSms() {
        val hash = "ABCDEF0123456789".repeat(4)
        val incident = "12345678901234567890"
        val body = EvidenceHashSms.body(incident, hash)
        assertTrue(body.contains("Incident: $incident"))
        assertTrue(body.endsWith("abcdef0123456789".repeat(4)))
        assertTrue(body.contains("SHA-256 (encrypted file):"))
        assertTrue(body.length <= 160)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteHashIsNeverSentAsFinalFingerprint() {
        EvidenceHashSms.body("1234567890123", "abcdef")
    }
}
