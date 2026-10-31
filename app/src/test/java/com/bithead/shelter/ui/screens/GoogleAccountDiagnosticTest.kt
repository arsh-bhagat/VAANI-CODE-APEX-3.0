package com.bithead.shelter.ui.screens

import androidx.credentials.exceptions.GetCredentialCustomException
import org.junit.Assert.*
import org.junit.Test

class GoogleAccountDiagnosticTest {
    @Test fun extractsBracketedStatusWithoutExposingProviderMessage() {
        val diagnostic = accountDiagnostic(GetCredentialCustomException(
            "provider.oauth", "Rejected [28404] for asha@example.com token=private-token"
        ))
        assertEquals("GetCredentialCustomException / 28404 / provider.oauth", diagnostic)
        assertFalse(diagnostic.contains("asha@example.com"))
        assertFalse(diagnostic.contains("private-token"))
    }

    @Test fun extractsColonDelimitedStatus() {
        val diagnostic = accountDiagnostic(GetCredentialCustomException("provider.oauth", "Status: 16: failure"))
        assertEquals("GetCredentialCustomException / 16 / provider.oauth", diagnostic)
    }

    @Test fun missingStatusDoesNotExposeRawErrorText() {
        val diagnostic = accountDiagnostic(GetCredentialCustomException("provider.oauth", "asha@example.com unavailable"))
        assertEquals("GetCredentialCustomException / provider.oauth", diagnostic)
    }

    @Test fun providerTypeIsBoundedAndFiltered() {
        val diagnostic = accountDiagnostic(GetCredentialCustomException("x".repeat(150) + "/bad type!", null))
        val type = diagnostic.substringAfterLast(" / ")
        assertTrue(type.length <= 100)
        assertTrue(type.all { it.isLetterOrDigit() || it in "._" })
    }
}
