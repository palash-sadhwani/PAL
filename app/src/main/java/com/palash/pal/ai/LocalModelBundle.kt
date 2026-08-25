package com.palash.pal.ai

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest

class LocalModelBundle(private val context: Context) {
    /**
     * Return the expected local path for the model file without blocking.
     * If the model is not present this method returns the target path and does not wait
     * for a download to complete. Call [startBackgroundDownload] to begin downloading.
     */
    fun targetModelPath(): String {
        val modelDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val target = File(modelDir, MODEL_FILE_NAME)
        return target.absolutePath
    }

    /**
     * If the model already exists or is packaged in assets, ensure it is present and return its path.
     * This does not start a network download — use [startBackgroundDownload] for that.
     */
    fun resolveIfAvailable(): String? {
        val modelDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val target = File(modelDir, MODEL_FILE_NAME)

        if (target.exists() && target.length() > 0L) {
            return target.absolutePath
        }

        try {
            context.assets.open("models/$MODEL_FILE_NAME").use { assetStream ->
                FileOutputStream(target).use { output -> assetStream.copyTo(output) }
            }
            return target.absolutePath
        } catch (e: Exception) {
            Log.d(TAG, "Model not packaged in assets: ${e.message}")
        }

        return null
    }

    /**
     * Start a non-blocking background download. Returns the target file path immediately.
     * progressCallback receives integers 0..100. completionCallback is invoked on finish or failure.
     */
    @Volatile
    private var isCancelled = false

    fun cancelDownload() {
        isCancelled = true
    }

    fun startBackgroundDownload(
        progressCallback: (Int) -> Unit = {},
        completionCallback: (success: Boolean, path: String?) -> Unit = { _, _ -> }
    ): String {
        val modelDir = File(context.filesDir, MODELS_DIR).apply { mkdirs() }
        val target = File(modelDir, MODEL_FILE_NAME)
        val temp = File(modelDir, "$MODEL_FILE_NAME.tmp")

        Log.d(TAG, "Model download requested: url=${MODEL_DOWNLOAD_URL}, target=${target.absolutePath}, temp=${temp.absolutePath}, checksumUrl=${MODEL_DOWNLOAD_URL + ".sha256"}")

        // If already present, invoke completion immediately
        if (target.exists() && target.length() > 0L) {
            Log.d(TAG, "Model already present at ${target.absolutePath}")
            completionCallback(true, target.absolutePath)
            return target.absolutePath
        }

        // If packaged in assets, copy synchronously (fast) and finish
        try {
            context.assets.open("models/$MODEL_FILE_NAME").use { assetStream ->
                FileOutputStream(target).use { output -> assetStream.copyTo(output) }
            }
            Log.d(TAG, "Model copied from assets to ${target.absolutePath}")
            completionCallback(true, target.absolutePath)
            return target.absolutePath
        } catch (_: Exception) {
            // not packaged — continue to background download
        }

        // Launch background thread to download
        isCancelled = false
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(MODEL_DOWNLOAD_URL)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    doInput = true
                }

                val responseCode = connection.responseCode
                val contentLength = connection.contentLengthLong
                Log.d(TAG, "Model download HTTP response: code=$responseCode, contentLength=$contentLength, finalUrl=${connection.url}")

                if (responseCode !in 200..299) {
                    val message = connection.responseMessage ?: "unknown"
                    throw IOException("Model download failed with HTTP $responseCode: $message")
                }

                val input = connection.inputStream

                // compute digest while streaming
                val md = MessageDigest.getInstance("SHA-256")
                DigestInputStream(input, md).use { digestStream ->
                    FileOutputStream(temp).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded: Long = 0
                        while (digestStream.read(buf).also { read = it } > 0) {
                            if (isCancelled) {
                                Log.w(TAG, "Model download cancelled before completion: target=${target.absolutePath}, temp=${temp.absolutePath}")
                                try { temp.delete() } catch (_: Exception) {}
                                completionCallback(false, null)
                                return@Thread
                            }
                            fos.write(buf, 0, read)
                            downloaded += read
                            if (contentLength > 0) {
                                val percent = ((downloaded * 100) / contentLength).toInt()
                                progressCallback(percent)
                            }
                        }
                        fos.fd.sync()
                    }
                }

                val expected = fetchExpectedChecksumOrNull()
                val computed = md.digest().toHexString()

                if (isCancelled) {
                    Log.w(TAG, "Model download cancelled after streaming: target=${target.absolutePath}, temp=${temp.absolutePath}")
                    try { temp.delete() } catch (_: Exception) {}
                    completionCallback(false, null)
                    return@Thread
                }

                if (expected != null) {
                    if (!computed.equals(expected, ignoreCase = true)) {
                        temp.delete()
                        Log.e(TAG, "Checksum mismatch. expected=$expected computed=$computed")
                        completionCallback(false, null)
                        return@Thread
                    }
                    Log.d(TAG, "Checksum validation succeeded. expected=$expected computed=$computed")
                } else {
                    Log.i(TAG, "Downloaded model checksum: $computed (checksum verification unavailable)")
                }

                // rename temp -> target
                if (temp.renameTo(target)) {
                    Log.d(TAG, "Model download complete. Saved to ${target.absolutePath}")
                    progressCallback(100)
                    completionCallback(true, target.absolutePath)
                } else {
                    Log.e(TAG, "Failed to move temp file ${temp.absolutePath} to ${target.absolutePath}")
                    temp.delete()
                    completionCallback(false, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed: type=${e::class.java.simpleName}, message=${e.message}, cancelled=${isCancelled}, target=${target.absolutePath}, temp=${temp.absolutePath}", e)
                try { temp.delete() } catch (_: Exception) {}
                completionCallback(false, null)
            } finally {
                connection?.disconnect()
            }
        }.apply { isDaemon = true }.start()

        return target.absolutePath
    }

    private fun fetchExpectedChecksumOrNull(): String? {
        val checksumUrl = MODEL_DOWNLOAD_URL + ".sha256"
        Log.d(TAG, "Checking checksum URL: $checksumUrl")
        return try {
            val conn = URL(checksumUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.instanceFollowRedirects = true
            val responseCode = conn.responseCode
            Log.d(TAG, "Checksum HTTP response: url=$checksumUrl, code=$responseCode")
            if (responseCode != 200) {
                conn.disconnect()
                Log.w(TAG, "Checksum validation unavailable: checksumUrl=$checksumUrl, responseCode=$responseCode")
                return null
            }
            conn.inputStream.bufferedReader().use { reader ->
                val line = reader.readLine()
                val hash = line?.split(Regex("\\s+"), limit = 2)?.firstOrNull()
                Log.d(TAG, "Checksum file parsed: responseCode=$responseCode, hash=${hash ?: "<missing>"}")
                hash
            }
        } catch (e: Exception) {
            Log.d(TAG, "No checksum available: type=${e::class.java.simpleName}, message=${e.message}")
            null
        }
    }

    private fun ByteArray.toHexString(): String {
        val sb = StringBuilder()
        for (b in this) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    companion object {
        private const val TAG = "LocalModelBundle"
        const val MODELS_DIR = "models"
        const val MODEL_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_DOWNLOAD_URL =
            "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }
}
