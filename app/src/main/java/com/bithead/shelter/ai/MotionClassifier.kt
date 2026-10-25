package com.bithead.shelter.ai

import android.content.Context
import com.bithead.shelter.sensors.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.json.JSONObject
import android.util.Log

/** Version 1 contract: normalized raw acceleration + gyro, FLOAT32 I/O, N/D/S output. */
class MotionClassifier(
    context: Context,
    asset: String = MotionContract.ASSET,
    private val classCount: Int = 3
) : AutoCloseable {
    private val interpreter: Interpreter

    init {
        require(classCount == 2 || classCount == 3)
        val bytes = context.assets.open(asset).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        model.rewind()
        val loaded = Interpreter(model, Interpreter.Options().setNumThreads(1))
        try {
            require(loaded.inputTensorCount == 1 && loaded.outputTensorCount == 1)
            require(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, 100, 6)))
            require(loaded.getOutputTensor(0).shape().contentEquals(intArrayOf(1, classCount)))
            require(loaded.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(loaded.getOutputTensor(0).dataType() == DataType.FLOAT32)
        } catch (error: Exception) { loaded.close(); throw error }
        interpreter = loaded
    }

    fun classify(frames: List<ImuFrame>): MotionPrediction {
        val input = ByteBuffer.allocateDirect(100 * 6 * 4).order(ByteOrder.nativeOrder())
        MotionContract.normalize(frames).forEach { input.putFloat(it) }
        input.rewind()
        val output = arrayOf(FloatArray(classCount))
        interpreter.run(input, output)
        return MotionPrediction.from(if (classCount == 2) output[0] + floatArrayOf(0f) else output[0])
    }

    override fun close() = interpreter.close()

    companion object {
        const val LOOSE_DROP_ASSET = "loose_phone_drop_classifier.tflite"
        const val LOOSE_DROP_MANIFEST = "loose_phone_drop_classifier.json"

        /** Missing/invalid assets retain the existing rules; never load a placeholder model. */
        fun looseDropOrNull(context: Context): MotionClassifier? {
            return runCatching {
                val assets = context.assets.list("").orEmpty().toSet()
                if (LOOSE_DROP_ASSET !in assets || LOOSE_DROP_MANIFEST !in assets) return null
                val manifest = JSONObject(context.assets.open(LOOSE_DROP_MANIFEST).bufferedReader().use { it.readText() })
                require(manifest.getString("task") == "loose_phone_drop_v1")
                val classes = manifest.getJSONArray("classes")
                require(classes.length() == 2 && classes.getString(0) == "NORMAL" && classes.getString(1) == "DROP")
                val shape = manifest.getJSONArray("input_shape")
                require(shape.length() == 3 && (0..2).map { shape.getInt(it) } == listOf(1, 100, 6))
                require(manifest.getInt("sample_rate_hz") == 50)
                require(manifest.getString("acceleration") == "raw_including_gravity_m_s2")
                require(manifest.getString("gyroscope") == "rad_s")
                require(kotlin.math.abs(manifest.getDouble("threshold") - 0.80) < 0.000001)
                val bytes = context.assets.open(LOOSE_DROP_ASSET).use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                require(hash == manifest.getString("model_sha256"))
                MotionClassifier(context, LOOSE_DROP_ASSET, 2)
            }.getOrElse {
                Log.w("MotionClassifier", "Loose-drop model unavailable; using motion rules", it)
                null
            }
        }
    }
}
