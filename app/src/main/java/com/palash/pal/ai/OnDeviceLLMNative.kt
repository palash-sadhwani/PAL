package com.palash.pal.ai

class OnDeviceLLMNative {
    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("pal_llama")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            libraryLoaded = false
        }
    }

    fun isLibraryAvailable(): Boolean = libraryLoaded

    fun loadModel(modelPath: String): Boolean {
        return nativeLoadModel(modelPath)
    }

    fun generate(prompt: String): String {
        return nativeGenerate(prompt)
    }

    fun unloadModel() {
        nativeUnloadModel()
    }

    fun cancelGeneration() {
        nativeCancelGeneration()
    }

    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeGenerate(prompt: String): String
    private external fun nativeUnloadModel()
    private external fun nativeCancelGeneration()
}
