package com.bithead.shelter.mesh

import android.content.Context

/** Google sign-in maps its account result here; credentials never enter the mesh layer.
 * Google basic sign-in does not normally provide a phone number; ask the user separately.
 */
data class AccountProfileSeed(val displayName: String, val userProvidedPhone: String? = null, val email: String? = null)

interface MeshProfileSource {
    fun read(): MeshOwnerProfile
    fun save(profile: MeshOwnerProfile)
    fun applyAccountProfile(account: AccountProfileSeed)
    fun observeAccountChanges(listener: () -> Unit): java.io.Closeable
    fun clearAccountProfile()
}

class LocalMeshProfileSource(context: Context) : MeshProfileSource {
    private val prefs = context.getSharedPreferences("vaani_mesh_profile", Context.MODE_PRIVATE)
    fun accountEmail(): String? = prefs.getString("account_email", null)
    override fun read() = MeshOwnerProfile(prefs.getString("name", "").orEmpty(),
        prefs.getString("phone", "").orEmpty(), prefs.getBoolean("share_phone", false), prefs.getBoolean("share_location", false),
        prefs.getString("email", "").orEmpty(), prefs.getBoolean("share_email", false))
    override fun save(profile: MeshOwnerProfile) {
        require(MeshCodec.validName(profile.displayName) && MeshCodec.validPhone(profile.phone) && MeshCodec.validEmail(profile.email))
        prefs.edit().putString("name", profile.displayName.trim()).putString("phone", profile.phone.trim())
            .putBoolean("share_phone", profile.sharePhone).putBoolean("share_location", profile.shareLocation)
            .putString("email", profile.email.trim()).putBoolean("share_email", profile.shareEmail).apply()
    }
    override fun applyAccountProfile(account: AccountProfileSeed) {
        // Account import never silently opts a user into broadcasting contact or location.
        save(MeshOwnerProfile(account.displayName.take(48), account.userProvidedPhone.orEmpty().take(32),
            email = account.email.orEmpty().take(120)))
        prefs.edit().putString("account_email", account.email)
            .putLong("account_generation", prefs.getLong("account_generation", 0) + 1).apply()
    }
    override fun observeAccountChanges(listener: () -> Unit): java.io.Closeable {
        val observer = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "account_generation") listener()
        }
        prefs.registerOnSharedPreferenceChangeListener(observer)
        return java.io.Closeable { prefs.unregisterOnSharedPreferenceChangeListener(observer) }
    }
    override fun clearAccountProfile() {
        val generation = prefs.getLong("account_generation", 0) + 1
        prefs.edit().clear().putLong("account_generation", generation).apply()
    }
}
