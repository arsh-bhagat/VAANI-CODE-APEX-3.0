package com.bithead.shelter.ui.walkthrough

import androidx.annotation.StringRes
import com.bithead.shelter.R

/** Stable feature IDs; strings are resolved in composition, never cached in the model. */
enum class FeatureHelp(@StringRes val title: Int, @StringRes val description: Int) {
    NAVIGATION(R.string.feature_navigation_title, R.string.feature_navigation_body),
    SAFEWORD(R.string.feature_safeword_title, R.string.feature_safeword_body),
    ALERT(R.string.feature_alert_title, R.string.feature_alert_body),
    CONTACTS(R.string.feature_contacts_title, R.string.feature_contacts_body),
    VAULT(R.string.feature_vault_title, R.string.feature_vault_body),
    CAMERA(R.string.feature_camera_title, R.string.feature_camera_body),
    MAP(R.string.feature_map_title, R.string.feature_map_body),
    SUPPORT(R.string.feature_support_title, R.string.feature_support_body),
    DECOY(R.string.feature_decoy_title, R.string.feature_decoy_body),
    DISGUISE(R.string.feature_disguise_title, R.string.feature_disguise_body),
    REPLAY(R.string.feature_replay_title, R.string.feature_replay_body),
    GESTURE(R.string.feature_gesture_title, R.string.feature_gesture_body),
    LOCATION(R.string.feature_location_title, R.string.feature_location_body),
    SECURITY(R.string.feature_security_title, R.string.feature_security_body),
    DISPATCH(R.string.feature_dispatch_title, R.string.feature_dispatch_body),
    INTEGRITY(R.string.feature_integrity_title, R.string.feature_integrity_body),
    BLACKOUT(R.string.feature_blackout_title, R.string.feature_blackout_body)
}

data class WalkthroughStep(val feature: FeatureHelp, val tab: Int, val itemKey: String)

val walkthroughSteps = listOf(
    WalkthroughStep(FeatureHelp.NAVIGATION, 0, "alert"),
    WalkthroughStep(FeatureHelp.SAFEWORD, 0, "safeword"),
    WalkthroughStep(FeatureHelp.ALERT, 0, "alert"),
    WalkthroughStep(FeatureHelp.CONTACTS, 4, "contacts"),
    WalkthroughStep(FeatureHelp.VAULT, 1, "vault"),
    WalkthroughStep(FeatureHelp.CAMERA, 1, "media"),
    WalkthroughStep(FeatureHelp.MAP, 2, "map"),
    WalkthroughStep(FeatureHelp.SUPPORT, 3, "support"),
    WalkthroughStep(FeatureHelp.DECOY, 0, "decoy"),
    WalkthroughStep(FeatureHelp.DISGUISE, 4, "disguise"),
    WalkthroughStep(FeatureHelp.REPLAY, 4, "help")
)
