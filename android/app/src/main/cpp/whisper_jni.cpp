#include <jni.h>
#include <whisper.h>
#include <algorithm>
#include <memory>
#include <string>
#include <thread>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>

namespace {
struct UtfChars {
    JNIEnv *env; jstring source; const char *value;
    UtfChars(JNIEnv *e, jstring s): env(e), source(s), value(e->GetStringUTFChars(s, nullptr)) {}
    ~UtfChars() { if (value) env->ReleaseStringUTFChars(source, value); }
};
struct AudioMap {
    int fd = -1; size_t size = 0; void *data = MAP_FAILED;
    ~AudioMap() { if (data != MAP_FAILED) munmap(data, size); if (fd >= 0) close(fd); }
};
struct Callback {
    JNIEnv *env; jobject instance; jmethodID cancelled; jmethodID progress;
    bool isCancelled() { return env->CallBooleanMethod(instance, cancelled) || env->ExceptionCheck(); }
};
void fail(JNIEnv *env, const char *message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_bigdictaphone_app_services_WhisperNative_transcribe(
    JNIEnv *env, jobject, jstring modelPath, jstring audioPath, jstring language, jobject listener) {
    try {
        UtfChars model(env, modelPath), audio(env, audioPath), lang(env, language);
        if (!model.value || !audio.value || !lang.value) return nullptr;
        auto cls = env->GetObjectClass(listener);
        Callback cb{env, listener, env->GetMethodID(cls, "isCancelled", "()Z"),
                    env->GetMethodID(cls, "onProgress", "(I)V")};
        if (env->ExceptionCheck() || cb.isCancelled()) return nullptr;
        AudioMap pcm;
        pcm.fd = open(audio.value, O_RDONLY);
        struct stat info{};
        if (pcm.fd < 0 || fstat(pcm.fd, &info) != 0 || info.st_size <= 0 ||
            info.st_size % sizeof(float) != 0 || info.st_size > 16000LL * 4 * 7200) {
            fail(env, "Invalid decoded audio (maximum recording length is two hours)."); return nullptr;
        }
        pcm.size = static_cast<size_t>(info.st_size);
        pcm.data = mmap(nullptr, pcm.size, PROT_READ, MAP_PRIVATE, pcm.fd, 0);
        if (pcm.data == MAP_FAILED) { fail(env, "Not enough memory to read this recording."); return nullptr; }
        auto options = whisper_context_default_params();
        options.use_gpu = false;
        std::unique_ptr<whisper_context, decltype(&whisper_free)> ctx(
            whisper_init_from_file_with_params(model.value, options), whisper_free);
        if (!ctx) { fail(env, "Could not load the local model. Download it again in Settings."); return nullptr; }
        if (cb.isCancelled()) return nullptr;
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
        params.language = lang.value;
        params.translate = false;
        params.no_context = true;
        params.print_realtime = params.print_progress = params.print_timestamps = params.print_special = false;
        params.suppress_blank = true;
        params.suppress_nst = true;
        // These callbacks execute on the caller thread; never use this JNIEnv on ggml worker threads.
        params.encoder_begin_callback = [](whisper_context *, whisper_state *, void *user) {
            return !static_cast<Callback *>(user)->isCancelled();
        };
        params.encoder_begin_callback_user_data = &cb;
        params.progress_callback = [](whisper_context *, whisper_state *, int value, void *user) {
            auto *c = static_cast<Callback *>(user);
            if (!c->env->ExceptionCheck()) c->env->CallVoidMethod(c->instance, c->progress, value);
        };
        params.progress_callback_user_data = &cb;
        int result = whisper_full(ctx.get(), params, static_cast<const float *>(pcm.data), pcm.size / sizeof(float));
        if (cb.isCancelled()) return nullptr;
        if (result != 0) { fail(env, "Local transcription failed. Try the smaller Tiny model."); return nullptr; }
        std::string text;
        for (int i = 0; i < whisper_full_n_segments(ctx.get()); ++i) text += whisper_full_get_segment_text(ctx.get(), i);
        auto bytes = env->NewByteArray(static_cast<jsize>(text.size()));
        if (bytes) env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte *>(text.data()));
        return bytes;
    } catch (const std::exception &) {
        fail(env, "Local transcription ran out of resources. Try the smaller Tiny model."); return nullptr;
    }
}
