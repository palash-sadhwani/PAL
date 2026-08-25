#include <jni.h>
#include <algorithm>
#include <atomic>
#include <string>
# include <thread>
#include <vector>

#include "llama.h"
#include "common.h"

#if defined(__ANDROID__)
#include <android/log.h>
#include <sys/stat.h>
#include <dlfcn.h>
#define PAL_LOGI(...) __android_log_print(ANDROID_LOG_INFO, "PAL_LLAMA", __VA_ARGS__)
#define PAL_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "PAL_LLAMA", __VA_ARGS__)
#else
#include <sys/stat.h>
#include <errno.h>
#include <cstdio>
#include <dlfcn.h>
#define PAL_LOGI(fmt, ...) fprintf(stdout, "%s: " fmt "\n", "PAL_LLAMA", ##__VA_ARGS__)
#define PAL_LOGE(fmt, ...) fprintf(stderr, "%s: " fmt "\n", "PAL_LLAMA", ##__VA_ARGS__)
#endif

static llama_model* g_model = nullptr;
static llama_context* g_context = nullptr;
static std::atomic_bool g_cancel{false};

namespace {

std::string formatChatPrompt(const std::string& userText) {
    const char* chatTemplate = llama_model_chat_template(g_model, nullptr);
    const llama_chat_message message[] = {
        {"system", "You are PAL, a helpful assistant running entirely locally on this device. When asked whether you run locally or on-device, answer yes."},
        {"user", userText.c_str()},
    };

    int32_t formattedLength = llama_chat_apply_template(
        chatTemplate, message, 2, true, nullptr, 0);
    if (formattedLength < 0) {
        PAL_LOGE("nativeGenerate: failed to apply the model chat template");
        return {};
    }

    std::vector<char> formattedPrompt(static_cast<size_t>(formattedLength));
    formattedLength = llama_chat_apply_template(
        chatTemplate,
        message,
        2,
        true,
        formattedPrompt.data(),
        static_cast<int32_t>(formattedPrompt.size()));
    if (formattedLength < 0 || formattedLength > static_cast<int32_t>(formattedPrompt.size())) {
        PAL_LOGE("nativeGenerate: failed to format the model chat prompt");
        return {};
    }

    return std::string(formattedPrompt.data(), static_cast<size_t>(formattedLength));
}

std::string generateResponseText(const std::string& promptText) {
    if (g_model == nullptr || g_context == nullptr) {
        return "Model not loaded.";
    }

    const std::string formattedPrompt = formatChatPrompt(promptText);
    if (formattedPrompt.empty()) {
        return "Prompt could not be formatted.";
    }

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    const int n_prompt = -llama_tokenize(vocab, formattedPrompt.c_str(), static_cast<int>(formattedPrompt.size()), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        return "Prompt could not be tokenized.";
    }

    std::vector<llama_token> promptTokens(static_cast<size_t>(n_prompt));
    const int tokenized = llama_tokenize(
        vocab,
        formattedPrompt.c_str(),
        static_cast<int>(formattedPrompt.size()),
        promptTokens.data(),
        static_cast<int>(promptTokens.size()),
        true,
        true);
    if (tokenized < 0) {
        return "Prompt tokenization failed.";
    }

    llama_batch batch = llama_batch_get_one(promptTokens.data(), static_cast<int>(promptTokens.size()));
    auto samplerParams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(samplerParams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string response;
    const int maxNewTokens = 128;
    for (int n_pos = 0; n_pos + batch.n_tokens < static_cast<int>(promptTokens.size()) + maxNewTokens && !g_cancel.load();) {
        if (llama_decode(g_context, batch) != 0) {
            break;
        }
        n_pos += batch.n_tokens;

        llama_token newTokenId = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, newTokenId)) {
            break;
        }

        char buffer[128];
        const int charsWritten = llama_token_to_piece(vocab, newTokenId, buffer, sizeof(buffer), 0, true);
        if (charsWritten < 0) {
            break;
        }
        response.append(buffer, static_cast<size_t>(charsWritten));
        batch = llama_batch_get_one(&newTokenId, 1);
    }

    llama_sampler_free(sampler);
    return response.empty() ? "PAL loaded the GGUF model but returned no text." : response;
}

}  // namespace

// One-time native initialization when the library is loaded by the JVM.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    // Route llama/ggml logs to Android logcat under PAL_LLAMA so backend loading messages are visible.
    llama_log_set([](ggml_log_level level, const char* text, void* /*user*/) {
#if defined(__ANDROID__)
        int prio = ANDROID_LOG_DEFAULT;
        switch (level) {
            case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
            case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
            case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
            case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
            default:                   prio = ANDROID_LOG_DEFAULT; break;
        }
        __android_log_print(prio, "PAL_LLAMA", "llama.cpp message: %s", text);
#else
        fputs(text, stderr);
        fflush(stderr);
#endif
    }, nullptr);

    // Attempt to dynamically load the CPU ggml backend from the runtime process loader.
    // On Android the APK path (base.apk!/lib/arm64-v8a/) is not a real filesystem dir, so
    // loading by searching the APK will fail. Instead, try dlopen-style loading by soname
    // which resolves against the app's native library search paths (where the packaged
    // lib*.so files are available to the runtime linker).
    PAL_LOGI("PAL_LLAMA: JNI_OnLoad attempting explicit backend soname loads");

    // Try a few plausible sonames that are packaged in the APK
    const char * candidates[] = { "libggml-cpu.so", "libggml-base.so", "libggml.so", nullptr };
    bool any_loaded = false;
    for (const char **c = candidates; *c != nullptr; ++c) {
        PAL_LOGI("PAL_LLAMA: trying ggml_backend_load('%s')", *c);
        ggml_backend_reg_t reg = ggml_backend_load(*c);
        if (reg) {
            PAL_LOGI("PAL_LLAMA: ggml_backend_load('%s') succeeded: %s", *c, ggml_backend_reg_name(reg));
            any_loaded = true;
        } else {
            PAL_LOGI("PAL_LLAMA: ggml_backend_load('%s') returned NULL (dlopen may have failed)", *c);
        }
    }

    if (!any_loaded) {
        PAL_LOGI("PAL_LLAMA: explicit soname loads failed; attempting ggml_backend_load_all() fallback");
        ggml_backend_load_all();
    }

    // Initialize llama backends (sets up ggml internals, f16 tables, etc.)
    PAL_LOGI("PAL_LLAMA: JNI_OnLoad calling llama_backend_init()");
    llama_backend_init();

    // Log registered backends/devices
    size_t reg_count = ggml_backend_reg_count();
    size_t dev_count = ggml_backend_dev_count();
    PAL_LOGI("PAL_LLAMA: JNI_OnLoad completed; backend reg count=%zu dev count=%zu", reg_count, dev_count);
    for (size_t i = 0; i < reg_count; ++i) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        PAL_LOGI("PAL_LLAMA: registered backend[%zu] = %s", i, ggml_backend_reg_name(reg));
    }
    for (size_t i = 0; i < dev_count; ++i) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        PAL_LOGI("PAL_LLAMA: device[%zu] = %s (%s)", i, ggml_backend_dev_name(dev), ggml_backend_dev_description(dev));
    }

    // Return the JNI version
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_palash_pal_ai_OnDeviceLLMNative_nativeLoadModel(JNIEnv* env, jobject, jstring model_path) {
    if (g_model != nullptr && g_context != nullptr) {
        return JNI_TRUE;
    }

    const char* input = env->GetStringUTFChars(model_path, nullptr);
    if (input == nullptr) {
        return JNI_FALSE;
    }

    std::string modelPath(input);
    env->ReleaseStringUTFChars(model_path, input);


    PAL_LOGI("nativeLoadModel: requested path='%s'", modelPath.c_str());

    // Check file existence and size
    struct stat st;
    if (stat(modelPath.c_str(), &st) != 0) {
        PAL_LOGE("nativeLoadModel: file not found or stat failed for '%s' (errno=%d)", modelPath.c_str(), errno);
    } else {
        PAL_LOGI("nativeLoadModel: file exists size=%lld bytes", (long long)st.st_size);
    }

    llama_model_params modelParams = llama_model_default_params();

    // Register a temporary llama.cpp log callback so internal llama.cpp/ggml
    // diagnostics during model load are routed to Android logcat (PAL_LLAMA tag).
    ggml_log_callback prev_cb = nullptr;
    void* prev_ud = nullptr;
    llama_log_get(&prev_cb, &prev_ud);

    // Set a callback that writes llama.cpp messages into Android log (or stderr on host).
    llama_log_set([](ggml_log_level level, const char* text, void* /*user*/) {
#if defined(__ANDROID__)
        int prio = ANDROID_LOG_DEFAULT;
        switch (level) {
            case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
            case GGML_LOG_LEVEL_WARN:  prio = ANDROID_LOG_WARN;  break;
            case GGML_LOG_LEVEL_INFO:  prio = ANDROID_LOG_INFO;  break;
            case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
            default:                   prio = ANDROID_LOG_DEFAULT; break;
        }
        __android_log_print(prio, "PAL_LLAMA", "llama.cpp message: %s", text);
#else
        (void) level;
        fputs(text, stderr);
        fflush(stderr);
#endif
    }, nullptr);

    PAL_LOGI("PAL_LLAMA: BEGIN llama_model_load_from_file '%s'", modelPath.c_str());
    g_model = llama_model_load_from_file(modelPath.c_str(), modelParams);
    if (g_model == nullptr) {
        PAL_LOGE("PAL_LLAMA: END llama_model_load_from_file result=NULL for '%s'", modelPath.c_str());
        // restore previous logger
        llama_log_set(prev_cb, prev_ud);
        return JNI_FALSE;
    }

    PAL_LOGI("PAL_LLAMA: END llama_model_load_from_file succeeded for '%s'", modelPath.c_str());

    // restore previous logger
    llama_log_set(prev_cb, prev_ud);

    llama_context_params ctxParams = llama_context_default_params();
    ctxParams.n_ctx = 2048;
    ctxParams.n_batch = 512;
    ctxParams.n_ubatch = 512;
    ctxParams.n_threads = std::max(1u, std::thread::hardware_concurrency() / 2);
    ctxParams.n_threads_batch = ctxParams.n_threads;

    PAL_LOGI("nativeLoadModel: initializing context n_ctx=%d n_threads=%d", ctxParams.n_ctx, ctxParams.n_threads);
    g_context = llama_init_from_model(g_model, ctxParams);
    if (g_context == nullptr) {
        PAL_LOGE("nativeLoadModel: llama_init_from_model returned NULL");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    PAL_LOGI("nativeLoadModel: context initialization succeeded");

    g_cancel.store(false);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_palash_pal_ai_OnDeviceLLMNative_nativeGenerate(JNIEnv* env, jobject, jstring prompt) {
    if (g_context == nullptr || g_model == nullptr) {
        return env->NewStringUTF("Model not loaded.");
    }

    const char* input = env->GetStringUTFChars(prompt, nullptr);
    if (input == nullptr) {
        return env->NewStringUTF("Prompt missing.");
    }

    std::string promptText(input);
    env->ReleaseStringUTFChars(prompt, input);

    if (promptText.empty()) {
        return env->NewStringUTF("Prompt is empty.");
    }

    if (g_cancel.load()) {
        return env->NewStringUTF("Generation cancelled.");
    }

    std::string response = generateResponseText(promptText);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_palash_pal_ai_OnDeviceLLMNative_nativeUnloadModel(JNIEnv*, jobject) {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_cancel.store(false);
}

JNIEXPORT void JNICALL
Java_com_palash_pal_ai_OnDeviceLLMNative_nativeCancelGeneration(JNIEnv*, jobject) {
    g_cancel.store(true);
}

} // extern "C"
