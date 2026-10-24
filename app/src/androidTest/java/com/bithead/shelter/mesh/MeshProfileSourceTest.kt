package com.bithead.shelter.mesh

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class MeshProfileSourceTest {
    @Test fun accountImportAndSignoutDoNotRetainSharingConsent() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "mesh_test_" + java.util.UUID.randomUUID()
        val context = object : android.content.ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(key, mode)
        }
        try {
            val source: MeshProfileSource = LocalMeshProfileSource(context)
            source.save(MeshOwnerProfile("Local", "+91 123", true, true))
            source.applyAccountProfile(AccountProfileSeed("Account name", email = "asha@example.com"))
            assertEquals("Account name", source.read().displayName)
            assertFalse(source.read().sharePhone); assertFalse(source.read().shareLocation); assertFalse(source.read().shareEmail)
            assertEquals("asha@example.com", source.read().email)
            assertEquals("", source.read().phone)
            source.clearAccountProfile()
            assertEquals(MeshOwnerProfile(), source.read())
        } finally { base.deleteSharedPreferences(key) }
    }
}
