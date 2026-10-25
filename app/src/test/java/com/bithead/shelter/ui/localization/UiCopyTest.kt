package com.bithead.shelter.ui.localization

import org.junit.Assert.assertEquals
import org.junit.Test

class UiCopyTest {
    @Test fun translatesNavigationAndKeepsEnglishFallback() {
        assertEquals("तिजोरी", translateUi("Vault", "hi"))
        assertEquals("मदत", translateUi("Support", "mr"))
        assertEquals("Vault", translateUi("Vault", "en"))
        assertEquals("Vault", translateUi("Vault", "unknown"))
        assertEquals("Unknown device message", translateUi("Unknown device message", "hi"))
    }

    @Test fun countdownAndEvidenceIdentifiersSurviveTranslation() {
        assertEquals("सुनने का समय: 15 सेकंड बाकी",
            translateUi("Listening window: 15s remaining", "hi"))
        assertEquals("ऐकण्याचा कालावधी: 7 सेकंद बाकी",
            translateUi("Listening window: 7s remaining", "mr"))
        assertEquals("व्हिडिओ पुरावा #42", translateUi("Video evidence #42", "mr"))
        assertEquals("घटना abc123", translateUi("INCIDENT abc123", "hi"))
    }

    @Test fun translatesSmsStatusWithoutChangingCanonicalStoredStatus() {
        val stored = "Evidence hash: Delivered"
        assertEquals("साक्ष्य हैश: पहुँच गया", translateUi(stored, "hi"))
        assertEquals("पुराव्याचा हॅश: पोहोचले", translateUi(stored, "mr"))
        assertEquals("Evidence hash: Delivered", stored)
    }

    @Test fun preservesDeviceDetailsAndDoesNotTreatArgumentsAsReplacementSyntax() {
        assertEquals("रिकॉर्डिंग शुरू हुई, लेकिन SMS असफल: modem $1\\error",
            translateUi("Recording started, but SMS failed: modem $1\\error", "hi"))
        assertEquals("⚠ छेडछाड आढळली: पुरावा #8 सापडत नाही",
            translateUi("⚠ Tamper detected: evidence #8 is missing", "mr"))
    }
}
