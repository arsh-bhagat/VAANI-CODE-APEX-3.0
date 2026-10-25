package com.bithead.shelter.emergency

import java.util.Locale

/** Fingerprint of the final encrypted file, not the vault chain hash or an AES key. */
object EvidenceHashSms {
    fun body(incidentId: String, fileSha256: String): String {
        require(incidentId.matches(Regex("[0-9]{1,20}"))) { "Invalid incident ID" }
        require(fileSha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid SHA-256 hash" }
        return "VAANI evidence sealed.\nIncident: $incidentId\nSHA-256 (encrypted file):\n${fileSha256.lowercase(Locale.ROOT)}"
    }
}
