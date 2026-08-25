# PAL Roadmap

## DONE
- Project package renamed to `com.palash.pal`
- Compose + Material 3 foundation
- Room conversation persistence
- Repository layer
- Placeholder local AI abstraction
- Agent abstraction
- Tool abstraction
- Retrieval abstraction
- Navigation
- Chat and settings screens
- Privacy-first manifest baseline

## CURRENT
- Phase 1 foundation implementation and validation
- Native on-device llama.cpp / GGUF integration scaffold added behind `OnDeviceAIEngine`

## PLANNED
- Finish Android NDK + CMake toolchain setup
- Download and validate a small GGUF model for Pixel 7a-class ARM64 devices
- Implement the JNI bridge and native llama.cpp build for the app
- Validate model load, generation, cancellation, and latency on a connected Pixel 7a
- Retrieval / RAG
- Embeddings
- Vector search
- OCR
- Voice input/output
- Controlled Android tools
- Personal memory
- Knowledge/document memory
