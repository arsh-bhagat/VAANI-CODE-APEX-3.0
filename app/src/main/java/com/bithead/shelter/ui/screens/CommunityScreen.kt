package com.bithead.shelter.ui.screens

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.bithead.shelter.ui.walkthrough.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.bithead.shelter.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bithead.shelter.ui.components.*
import com.bithead.shelter.mesh.MeshLocation
import com.bithead.shelter.ui.theme.*
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.math.abs
import kotlin.math.cos

internal data class MapDangerZone(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val level: String,
    val title: String,
    val detail: String
)

internal val sampleDangerZones = listOf(
    MapDangerZone(28.6421, 77.2194, 340.0, "high", "Harassment reports", "8 sample reports in the past 30 days"),
    MapDangerZone(28.6228, 77.2087, 270.0, "medium", "Poorly lit stretch", "5 sample reports in the past 30 days"),
    MapDangerZone(28.6356, 77.2315, 230.0, "medium", "Isolated route", "3 sample reports in the past 30 days")
)

internal fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val result = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, result)
    return result[0].toDouble()
}

private fun adaptiveDangerZones(latitude: Double?, longitude: Double?): List<MapDangerZone> {
    if (latitude == null || longitude == null ||
        !latitude.isFinite() || !longitude.isFinite() ||
        distanceMeters(latitude, longitude, 28.6335, 77.2199) <= 50_000.0) {
        return sampleDangerZones
    }

    fun shifted(northMeters: Double, eastMeters: Double, radius: Double, level: String, title: String, detail: String): MapDangerZone {
        val lat = (latitude + northMeters / 111_320.0).coerceIn(-89.999, 89.999)
        val longitudeScale = (111_320.0 * abs(cos(Math.toRadians(latitude)))).coerceAtLeast(1.0)
        val rawLongitude = longitude + eastMeters / longitudeScale
        val lng = ((rawLongitude + 540.0) % 360.0) - 180.0
        return MapDangerZone(lat, lng, radius, level, title, detail)
    }

    return listOf(
        shifted(120.0, 160.0, 340.0, "high", "Nearby incident cluster", "8 adaptive sample reports in this map area"),
        shifted(-420.0, -250.0, 270.0, "medium", "Poorly lit stretch", "5 adaptive sample reports in this map area"),
        shifted(480.0, -380.0, 230.0, "medium", "Isolated route", "3 adaptive sample reports in this map area")
    )
}

private fun dangerZonesJson(zones: List<MapDangerZone>) = JSONArray().apply {
    check(zones.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.radiusMeters > 0 })
    zones.forEach { zone ->
        put(JSONObject()
            .put("lat", zone.latitude)
            .put("lng", zone.longitude)
            .put("radius", zone.radiusMeters)
            .put("level", zone.level)
            .put("title", zone.title)
            .put("detail", zone.detail))
    }
}.toString()

private fun nearbyDangerZone(latitude: Double?, longitude: Double?, zones: List<MapDangerZone>): MapDangerZone? {
    if (latitude == null || longitude == null) return null
    return zones
        .minByOrNull { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        ?.takeIf { distanceMeters(latitude, longitude, it.latitude, it.longitude) <= it.radiusMeters }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LeafletMap(
    filter: String,
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    locationTimestamp: Long?,
    zones: List<MapDangerZone>,
    recenterRequest: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapReady by remember { mutableStateOf(false) }
    var mapLoadAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(mapLoadAttempt) {
        delay(8_000L)
        if (!mapReady && mapError == null) mapError = "Map did not finish loading. Tap Retry to reload it."
    }
    val checkMapSize: (WebView) -> Unit = { view ->
        if (view.width > 0 && view.height > 0) {
            view.evaluateJavascript(
                "window.invalidateMapSize && window.invalidateMapSize(); Boolean(window.isMapReady && window.isMapReady());"
            ) { result ->
                if (result == "true" || result == "\"true\"") {
                    mapReady = true
                    mapError = null
                }
            }
        }
    }
    val webView = remember(context, mapLoadAttempt) {
        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            settings.userAgentString = "${settings.userAgentString} VAANI-Android/1.0"
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMapReady() {
                    (context as? android.app.Activity)?.runOnUiThread {
                        mapReady = true
                        mapError = null
                    } ?: run {
                        mapReady = true
                        mapError = null
                    }
                }
            }, "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        Log.e("VaaniMap", "${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme != "https" || request.url.host != "appassets.androidplatform.net") return null
                    val path = request.url.path.orEmpty()
                    val asset = path.removePrefix("/assets/")
                    if (!path.startsWith("/assets/") || asset.contains("..") ||
                        (asset != "leaflet_map.html" && !asset.startsWith("leaflet/"))) {
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                    val mime = when {
                        asset.endsWith(".html") -> "text/html"
                        asset.endsWith(".css") -> "text/css"
                        asset.endsWith(".js") -> "text/javascript"
                        asset.endsWith(".png") -> "image/png"
                        else -> "text/plain"
                    }
                    return try {
                        WebResourceResponse(mime, if (mime.startsWith("text/")) "UTF-8" else null, context.assets.open(asset))
                    } catch (_: IOException) {
                        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    (view.tag as? String)?.let { view.evaluateJavascript(it, null) }
                    view.post { checkMapSize(view) }
                    view.postDelayed({ checkMapSize(view) }, 500L)
                    view.postDelayed({ checkMapSize(view) }, 1500L)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        mapReady = false
                        mapError = "Map page could not load. Tap Retry to try again."
                    }
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
            }
            addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right > left && bottom > top) {
                    if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                        view.post { checkMapSize(view as WebView) }
                    }
                }
            }
            loadUrl("https://appassets.androidplatform.net/assets/leaflet_map.html")
        }
    }
    val validLocation = latitude?.isFinite() == true && longitude?.isFinite() == true
    val zonesJson = remember(zones) { dangerZonesJson(zones) }
    val script = remember(filter, latitude, longitude, accuracyMeters, locationTimestamp, zonesJson, recenterRequest) {
        val lat = if (validLocation) latitude.toString() else "null"
        val lng = if (validLocation) longitude.toString() else "null"
        val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f }?.toString() ?: "null"
        val timestamp = locationTimestamp?.takeIf { it > 0L }?.toString() ?: "null"
        buildString {
            append("window.applyNativeState && window.applyNativeState(")
            append(JSONObject.quote(filter)).append(", ").append(lat).append(", ").append(lng)
            append(", ").append(accuracy).append(", ").append(timestamp).append(", ").append(zonesJson).append(");")
            append("window.requestRecenter && window.requestRecenter(").append(recenterRequest).append(");")
        }
    }
    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
        }
    }
    Box(modifier) {
        AndroidView(
            factory = { webView },
            update = { view ->
                if (view.tag != script) {
                    view.tag = script
                    view.evaluateJavascript(script, null)
                }
                view.post { checkMapSize(view) }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!mapReady) {
            Column(
                Modifier.fillMaxSize().background(ShelterSurface).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (mapError == null) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading community map…")
                } else {
                    Text(mapError ?: "Map unavailable")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        mapError = null
                        mapReady = false
                        mapLoadAttempt++
                    }) { Text("Retry map") }
                }
            }
        }
    }
}

@Composable
internal fun CommunityScreen(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Float?,
    locationTimestamp: Long?,
    refreshLocation: () -> Unit,
    info: (String) -> Unit
) {
    var meshOpen by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("All Signals") }
    var factor by rememberSaveable { mutableStateOf("Dark stretch") }
    var reports by rememberSaveable { mutableIntStateOf(0) }
    var recenterRequest by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val zones = remember(latitude, longitude) { adaptiveDangerZones(latitude, longitude) }
    val nearbyZone = remember(latitude, longitude, zones) { nearbyDangerZone(latitude, longitude, zones) }
    val tour = LocalWalkthrough.current
    LaunchedEffect(tour?.active) { if (tour?.active != true) refreshLocation() }
    LaunchedEffect(nearbyZone?.title, tour?.active) {
        if (tour?.active != true) nearbyZone?.let { info("Caution: you are inside the sample ${it.title.lowercase()} zone. Check local conditions and choose a well-lit route.") }
    }
    LaunchedEffect(tour?.active) { if (tour?.active == true) meshOpen = false }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(SurfaceLowest).padding(16.dp)) {
            if (meshOpen) {
                OutlinedButton(onClick = { meshOpen = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.mesh_back_to_map))
                }
            } else {
                BrutalButton(onClick = { meshOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp)) {
                    Icon(Icons.Outlined.BluetoothSearching, null, Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.mesh_entry_title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.mesh_entry_subtitle), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(24.dp))
                }
            }
        }
        if (meshOpen) {
            val point = if (latitude != null && longitude != null && locationTimestamp != null && accuracyMeters != null)
                MeshLocation(latitude, longitude, locationTimestamp, accuracyMeters) else null
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                RealtimeMeshPanel(point, refreshLocation)
            }
        } else {
    LazyColumn(modifier = Modifier.weight(1f), state = LocalWalkthrough.current?.lists?.get(2) ?: rememberLazyListState(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BrutalPanel(header = "01 / Community safe routes") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(SlateMesh))
                Spacer(Modifier.width(10.dp))
                Heading("Route intelligence")
            }
            StatusPill("Sample map overlays / nearby mesh available", tone = StatusTone.Mesh)
            Caption("Current GPS with illustrative community signals. Sample routes do not establish that a street is safe.")
        } }
        if (nearbyZone != null) item { Panel(color = ShelterDanger.copy(alpha = 0.18f)) { Heading("Caution near ${nearbyZone.title.lowercase()}"); Caption("Your current location overlaps this sample risk area. Use the map to check a well-lit alternative.") } }
        item(key = "map") {
            LeafletMap(
                filter,
                latitude,
                longitude,
                accuracyMeters,
                locationTimestamp,
                zones,
                recenterRequest,
                Modifier.fillMaxWidth().height(360.dp).featureTarget(FeatureHelp.MAP).neoShadow().brutalBorder().clip(RoundedCornerShape(4.dp))
            )
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = refreshLocation, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Refresh") }
            OutlinedButton(onClick = {
                if (latitude != null && longitude != null) recenterRequest++ else refreshLocation()
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.MyLocation, null); Spacer(Modifier.width(6.dp)); Text("Recenter") }
        } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All Signals", "Well-Lit Streets", "Patrol Points", "Safe Haven", "Risk Corridor").forEach { label -> FilterChip(filter == label, { filter = label }, shape = RoundedCornerShape(4.dp), label = { Text(label) }) } } }
        item { Panel { Heading("Safe Corridor Preview"); Badge("Adaptive sample data"); Panel(color = ShelterSurfaceRaised) { Heading("Suggested: Nearby Well-Lit Corridor"); Caption("Leaflet route overlay near the current map area • no live safety score"); Action("Open device maps", Icons.Outlined.NearMe, { openLink(context, "geo:0,0?q=nearby+police+station") }) }; Caption("Map tiles require internet access. Route availability and local conditions must still be checked.") } }
        item { Heading("COMMUNITY OBSERVATIONS") }
        item { Panel { Heading("Night transit shuttle"); Caption("Sample • Gate 4 station, regular service") } }
        item { Panel {
            Heading("Report environmental factor"); Caption("Try a local demo report. Nothing is published or sent.")
            listOf("Dark stretch", "Isolated alley", "Harassment hotspot", "Stray dogs").chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { text -> FilterChip(factor == text, { factor = text }, label = { Text(text, fontSize = 12.sp) }) } } }
            Action("Save demo signal", Icons.Outlined.AddLocationAlt, { reports++; info("Demo signal saved for this session: $factor. It has not been shared.") })
            Caption("$reports local demo signals this session")
        } }
        item { PreviewActionPanel("Nearby police stations", "Preview nearby public stations. This does not notify or dispatch police.", Icons.Outlined.LocalPolice) { info("Preview: no alert was sent. Opening nearby police stations in your device map."); openLink(context, "geo:0,0?q=nearby+police+station") } }
        item { PreviewActionPanel("Nearby NGOs", "Preview local support organizations. No organization is contacted from this panel.", Icons.Outlined.VolunteerActivism) { info("Preview: no alert was sent. Connect an approved recipient integration before using this for dispatch.") } }
        item { PreviewActionPanel("Peer support", "Preview consenting peer-support options. No peer is contacted from this panel.", Icons.Outlined.Groups) { info("Preview: no alert was sent. Peer support requires an approved, consent-based integration.") } }
        item { PreviewActionPanel("Nearby shelters", "Preview local shelters and public resources. No shelter is contacted from this panel.", Icons.Outlined.HomeWork) { info("Preview: no alert was sent. Opening this preview does not reserve a place or notify a shelter.") } }
    }
        }
    }
}
