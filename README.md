# PAL

PAL is a privacy-preserving, offline-first Android AI assistant foundation.

## Current Phase 1 features
- Kotlin + Jetpack Compose + Material 3
- Chat screen with local message persistence
- Room database for conversations and messages
- ViewModel, repository, AI, agent, and tool abstractions
- Settings screen
- Basic navigation
- Local-only placeholder AI engine

## Phase 2 in progress
- Native on-device LLM runtime integration via llama.cpp / ggml
- JNI bridge for loading a GGUF model on Android devices
- Selected model: Qwen2.5-1.5B-Instruct in GGUF Q4_K_M, suitable for a 8GB Pixel 7a
- Runtime model bundle that resolves a local GGUF file from app storage and falls back to a direct download if the model is not present
- Placeholder fallback for devices without the native library or without a valid GGUF bundle
- Retrieval / RAG
- Embeddings and vector search
- OCR
- Voice input and output
- Android action tools

## Privacy principles
- No cloud AI integration
- No analytics or telemetry
- No advertising SDKs
- Conversation data stays local

## Build and run
Open the project in Android Studio and run the `app` configuration on an Android device or emulator.

## Limitations
PAL Phase 1 is a foundation only. The app includes a local placeholder AI engine and a new native OnDeviceAIEngine scaffold for llama.cpp integration, but the true GGUF model runtime is not yet installed, built, or validated in this environment.
