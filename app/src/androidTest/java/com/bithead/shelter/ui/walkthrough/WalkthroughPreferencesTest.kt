package com.bithead.shelter.ui.walkthrough

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import com.bithead.shelter.data.WalkthroughPreferences
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WalkthroughPreferencesTest {
    @Test fun skipAndFinishPersistWithoutResettingReplayEligibility() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedName = "walkthrough_test_" + UUID.randomUUID()
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences(isolatedName, mode)
        }
        try {
            assertTrue(WalkthroughPreferences(context).shouldOffer)
            WalkthroughPreferences(context).handle(false)
            assertFalse(WalkthroughPreferences(context).shouldOffer)
            assertEquals(0, context.getSharedPreferences("", 0).getInt("completed_version", 0))
            WalkthroughPreferences(context).handle(true)
            assertEquals(WalkthroughPreferences.VERSION,
                context.getSharedPreferences("", 0).getInt("completed_version", 0))
            WalkthroughPreferences(context).handle(false)
            assertFalse(WalkthroughPreferences(context).shouldOffer)
            assertEquals(WalkthroughPreferences.VERSION,
                context.getSharedPreferences("", 0).getInt("completed_version", 0))
        } finally { target.deleteSharedPreferences(isolatedName) }
    }

    @Test fun featureCatalogHasDefaultFallbackAcrossLocales() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (locale in listOf("en", "hi", "ar", "fr")) {
            val configuration = Configuration(context.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(locale))
            }
            val localized = context.createConfigurationContext(configuration)
            for (feature in FeatureHelp.entries) {
                assertTrue(localized.getString(feature.title).isNotBlank())
                assertTrue(localized.getString(feature.description).isNotBlank())
            }
        }
        assertEquals(walkthroughSteps.size, walkthroughSteps.map { it.feature }.distinct().size)
    }
}
