# PAL Architecture

## Layering

```text
UI
↓
ViewModel
↓
Domain contracts
↓
Repositories / Agent
↓
Local data / AI / tools
```

## Key modules

- **ui/**: Compose screens and navigation
- **domain/**: models and contracts
- **data/**: Room persistence and repository implementation
- **ai/**: local AI engine implementation
- **agent/**: agent runtime implementation
- **tools/**: in-memory tool registry and permission boundary
- **memory/**: conversation, personal, and knowledge memory boundaries
- **retrieval/**: retrieval boundary for future document/context lookup

## Runtime flow

```text
User
→ ChatScreen
→ ChatViewModel
→ ChatRepository
→ Room
→ AgentRuntime
→ LocalAIEngine
→ ChatRepository
→ Room
→ UI update
```

## Notes

- Room entities stay inside the data layer.
- The UI only sees domain models and UI state.
- The agent layer is intentionally small for Phase 1.
- Tool execution must always pass through permission checks in future phases.
- Phase 2 introduces a native on-device runtime boundary through `OnDeviceAIEngine`, which is designed to call a JNI bridge backed by llama.cpp / GGUF inference. The native library and model remain an environment-validated dependency rather than a claimed shipped feature.
