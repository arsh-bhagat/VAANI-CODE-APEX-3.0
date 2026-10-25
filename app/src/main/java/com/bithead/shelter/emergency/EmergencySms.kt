package com.bithead.shelter.emergency

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.bithead.shelter.data.TrustedContact
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ContactAlertStatus(val name: String, val number: String, val status: String)

object EmergencySms {
    private const val PREFS = "vaani_sms_status"
    val statuses = MutableStateFlow<List<ContactAlertStatus>>(emptyList())

    fun restore(context: Context) {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("entries", "[]")
        statuses.value = runCatching {
            JSONArray(stored).let { array ->
                (0 until array.length()).map { index ->
                    array.getJSONObject(index).let {
                        ContactAlertStatus(it.getString("name"), it.getString("number"), it.getString("status"))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun send(context: Context, contacts: List<TrustedContact>, location: CachedLocation?, incidentId: String) {
        require(incidentId.matches(Regex("[0-9]{1,20}"))) { "Invalid incident ID" }
        val body = buildString {
            append("VAANI SOS: Please call me. ")
            if (location == null) append("Location unavailable.")
            else {
                append("Last known ")
                append(SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(location.timestampMillis)))
                append(": https://maps.google.com/?q=")
                append(String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude))
            }
            append("\nIncident: $incidentId")
        }
        dispatch(context, contacts, body, "SOS")
    }

    fun sendEvidenceHash(context: Context, contacts: List<TrustedContact>, incidentId: String, fileSha256: String) {
        dispatch(context, contacts, EvidenceHashSms.body(incidentId, fileSha256), "Evidence hash")
    }

    private fun dispatch(context: Context, contacts: List<TrustedContact>, body: String, label: String) {
        val batchId = UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("batch_id", batchId).apply()
        if (contacts.isEmpty()) {
            persist(context, emptyList())
            return
        }
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        val subscription = SubscriptionManager.getDefaultSmsSubscriptionId()
        val ready = permission && subscription != SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val initial = contacts.map {
            ContactAlertStatus(it.name, it.number, when {
                !permission -> "SMS permission missing"
                !ready -> "No default SMS SIM"
                else -> "Sending $label"
            })
        }
        persist(context, initial)
        if (!ready) return

        val manager = if (android.os.Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscription)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscription)
        }
        contacts.forEachIndexed { index, contact ->
            try {
                val parts = manager.divideMessage(body)
                // SOS and the full hash follow-up each fit a single SMS.
                require(parts.size == 1) { "Emergency message is too long for one SMS" }
                val request = (System.currentTimeMillis() % 100_000L).toInt() + index * 2
                val sent = PendingIntent.getBroadcast(context, request,
                    Intent(context, SmsStatusReceiver::class.java)
                        .setAction(SmsStatusReceiver.ACTION_SENT)
                        .setData(Uri.parse("vaani-sms://$batchId/$index/sent"))
                        .putExtra("number", contact.number).putExtra("batch_id", batchId).putExtra("label", label),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val delivered = PendingIntent.getBroadcast(context, request + 1,
                    Intent(context, SmsStatusReceiver::class.java)
                        .setAction(SmsStatusReceiver.ACTION_DELIVERED)
                        .setData(Uri.parse("vaani-sms://$batchId/$index/delivered"))
                        .putExtra("number", contact.number).putExtra("batch_id", batchId).putExtra("label", label),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.sendTextMessage(contact.number, null, body, sent, delivered)
            } catch (error: Exception) {
                update(context, contact.number, "Send failed: $label (${error.message ?: "SMS unavailable"})", batchId)
            }
        }
    }

    fun update(context: Context, number: String, status: String, batchId: String) {
        // A late SOS receipt must not be reported as delivery of the evidence hash.
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("batch_id", null) != batchId) return
        persist(context, statuses.value.map { if (it.number == number) it.copy(status = status) else it })
    }

    private fun persist(context: Context, values: List<ContactAlertStatus>) {
        statuses.value = values
        val array = JSONArray()
        values.forEach { array.put(JSONObject().put("name", it.name).put("number", it.number).put("status", it.status)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("entries", array.toString()).apply()
    }
}

class SmsStatusReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SENT = "com.bithead.shelter.SMS_SENT"
        const val ACTION_DELIVERED = "com.bithead.shelter.SMS_DELIVERED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra("number") ?: return
        val batchId = intent.getStringExtra("batch_id") ?: return
        val label = intent.getStringExtra("label") ?: "SMS"
        EmergencySms.restore(context)
        val status = when (intent.action) {
            ACTION_SENT -> if (resultCode == Activity.RESULT_OK) "$label: Sent" else "Send failed ($resultCode): $label"
            ACTION_DELIVERED -> if (resultCode == Activity.RESULT_OK) "$label: Delivered" else "$label: Sent; delivery unconfirmed"
            else -> return
        }
        EmergencySms.update(context, number, status, batchId)
    }
}
