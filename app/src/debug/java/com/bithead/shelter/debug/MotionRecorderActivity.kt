package com.bithead.shelter.debug

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.*
import com.bithead.shelter.sensors.ImuSampler
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Explicit opt-in debug tool. Never calls speech, emergency, SMS, or evidence APIs. */
class MotionRecorderActivity : Activity() {
    private lateinit var sampler: ImuSampler
    private lateinit var status: TextView
    private lateinit var participant: EditText
    private lateinit var labels: Spinner
    private lateinit var start: Button
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val rows = mutableListOf<String>()
    private var recording = false
    private var gaps = 0
    private var label = "NORMAL"
    private var participantId = ""
    private val timeout = Runnable { finishRecording() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }
        layout.addView(TextView(this).apply { text = "Motion dataset recorder (debug only)\nNo audio or alerts. DROP means the phone is released completely, not mounted to a body or rig. Record at least 2 seconds before and 1 second after each event; review the impact timestamp later. NORMAL should include setting down, shaking, pockets, bags and walking. Use a test device and padded landing area. Stops on leaving or after 5 minutes." })
        participant = EditText(this).apply { hint = "Anonymous participant ID (letters/digits/-/_)" }
        layout.addView(participant)
        labels = Spinner(this).apply { adapter = ArrayAdapter(this@MotionRecorderActivity, android.R.layout.simple_spinner_dropdown_item, listOf("NORMAL", "DROP", "SNATCH")) }
        layout.addView(labels)
        start = Button(this).apply { text = "Start recording"; setOnClickListener { beginRecording() } }
        layout.addView(start)
        layout.addView(Button(this).apply { text = "Stop and save"; setOnClickListener { finishRecording() } })
        status = TextView(this).apply { text = "Idle. CSV files are saved in private files/motion-dataset/." }
        layout.addView(status)
        setContentView(layout)
        sampler = ImuSampler(this, { if (recording) gaps++ }) { frame ->
            if (recording) {
                rows.add("${frame.timestampNs},${frame.values.joinToString(",")}")
                if (rows.size % 50 == 0) status.text = "Recording $label: ${rows.size} frames; $gaps gaps"
                if (rows.size >= 15_000) finishRecording()
            }
        }
    }

    private fun beginRecording() {
        if (recording) return
        val id = participant.text.toString().trim()
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,64}"))) {
            status.text = "Enter an anonymous participant ID first."; return
        }
        rows.clear(); gaps = 0
        label = labels.selectedItem.toString()
        participantId = id
        recording = true
        if (!sampler.start()) { recording = false; status.text = "Accelerometer and gyroscope are required."; return }
        start.isEnabled = false; participant.isEnabled = false; labels.isEnabled = false
        main.postDelayed(timeout, 300_000L)
        status.text = "Recording $label. Stop after your trial."
    }

    private fun finishRecording() {
        if (!recording) return
        recording = false
        sampler.stop()
        main.removeCallbacks(timeout)
        val snapshot = rows.toList()
        val session = UUID.randomUUID().toString()
        val trialLabel = label
        val trialParticipant = participantId
        val trialGaps = gaps
        status.text = "Saving ${snapshot.size} frames…"
        worker.execute {
            val saved = runCatching {
                val directory = File(filesDir, "motion-dataset").apply { check(isDirectory || mkdirs()) }
                val file = File(directory, "$session.csv")
                file.bufferedWriter().use { out ->
                    out.appendLine("# participant=$trialParticipant,session=$session,label=$trialLabel,device=${Build.MANUFACTURER} ${Build.MODEL},gaps=$trialGaps")
                    out.appendLine("timestamp_ns,ax,ay,az,gx,gy,gz")
                    snapshot.forEach { out.appendLine(it) }
                }
                file
            }
            main.post {
                start.isEnabled = true; participant.isEnabled = true; labels.isEnabled = true
                status.text = saved.fold({ "Saved ${snapshot.size} frames to motion-dataset/${it.name}" }, { "Save failed: ${it.message}" })
            }
        }
    }

    override fun onPause() { finishRecording(); super.onPause() }
    override fun onDestroy() { main.removeCallbacks(timeout); sampler.stop(); worker.shutdown(); super.onDestroy() }
}
