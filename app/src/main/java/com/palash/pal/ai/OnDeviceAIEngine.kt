package com.palash.pal.ai

import android.util.Log
import com.palash.pal.domain.ai.AIRequest
import com.palash.pal.domain.ai.AIResponse
import com.palash.pal.domain.ai.LocalAIEngine
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnDeviceAIEngine(
    private val nativeEngine: OnDeviceLLMNative = OnDeviceLLMNative(),
    private val fallbackEngine: LocalAIEngine = PlaceholderAIEngine(),
    private val modelPath: String? = null,
    private val modelResolver: (() -> String)? = null
) : LocalAIEngine {
    private var isModelLoaded = false

    override suspend fun generateResponse(request: AIRequest): AIResponse {
        val prompt = request.userMessage.content.trim()
        return withContext(Dispatchers.IO) {
            try {
                val resolvedModelPath = resolveModelPath()
                val modelFile = File(resolvedModelPath)
                if (!modelFile.exists() || modelFile.length() <= 0L) {
                    logWarning("MODEL_FILE_MISSING reason=missing_or_empty path=$resolvedModelPath size=${modelFile.length()}")
                    return@withContext AIResponse(content = "MODEL_NOT_READY: downloaded GGUF is not available yet at $resolvedModelPath")
                }

                if (!nativeEngine.isLibraryAvailable()) {
                    logError("NATIVE_LIBRARY_UNAVAILABLE path=$resolvedModelPath")
                    return@withContext AIResponse(content = "NATIVE_LIBRARY_UNAVAILABLE: local GGUF runtime is not available.")
                }

                if (!isModelLoaded) {
                    logDebug("NATIVE_MODEL_LOAD_ATTEMPT path=$resolvedModelPath size=${modelFile.length()}")
                    val loadResult = nativeEngine.loadModel(resolvedModelPath)
                    isModelLoaded = loadResult
                    logDebug("NATIVE_MODEL_LOAD_RESULT path=$resolvedModelPath success=$loadResult")
                    if (!loadResult) {
                        return@withContext AIResponse(content = "NATIVE_MODEL_LOAD_FAILED: failed to load GGUF from $resolvedModelPath")
                    }
                }

                val generationPrompt = prompt.ifBlank { "Please respond briefly and clearly." }
                logDebug("GENERATE_INVOCATION path=$resolvedModelPath promptLength=${generationPrompt.length}")
                val content = nativeEngine.generate(generationPrompt)
                val output = content.ifBlank {
                    "PAL loaded the GGUF model but returned empty output."
                }
                logDebug("REAL_INFERENCE_SUCCESS path=$resolvedModelPath outputLength=${output.length}")
                AIResponse(content = output)
            } catch (e: UnsatisfiedLinkError) {
                logError("JNI_ERROR type=${e::class.java.simpleName} message=${e.message}")
                AIResponse(content = "JNI_ERROR: native library failed to initialize: ${e.message}")
            } catch (e: IllegalStateException) {
                logError("JNI_ERROR type=${e::class.java.simpleName} message=${e.message}")
                AIResponse(content = "JNI_ERROR: native model state failed: ${e.message}")
            } catch (e: Throwable) {
                logError("GENERATION_ERROR type=${e::class.java.simpleName} message=${e.message}")
                AIResponse(content = "GENERATION_ERROR: ${e::class.java.simpleName}: ${e.message}")
            }
        }
    }

    fun unload() {
        if (isModelLoaded) {
            nativeEngine.unloadModel()
            isModelLoaded = false
        }
    }

    fun cancel() {
        nativeEngine.cancelGeneration()
    }

    private fun resolveModelPath(): String {
        val direct = modelPath
        if (direct != null) {
            logDebug("MODEL_PATH_RESOLVED source=explicit path=$direct")
            return direct
        }

        val resolved = modelResolver?.invoke() ?: defaultModelPath()
        logDebug("MODEL_PATH_RESOLVED source=${if (modelResolver != null) "resolver" else "default"} path=$resolved")
        return resolved
    }

    private fun defaultModelPath(): String {
        return "file:///android_asset/models/qwen2.5-1.5b-instruct-q4_k_m.gguf"
    }

    private fun logDebug(message: String) {
        runCatching { Log.d(TAG, message) }
    }

    private fun logWarning(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun logError(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    companion object {
        private const val TAG = "OnDeviceAIEngine"
    }
}
