#include <jni.h>
#include <archive.h>
#include <archive_entry.h>
#include <unistd.h>
#include <sys/stat.h>
#include <string>
#include <vector>
#include <stdexcept>
#include <memory>
#include <codecvt>
#include <locale>

namespace {
constexpr int MAX_ENTRIES = 20000;
constexpr int64_t MAX_EXPANDED = 8LL * 1024 * 1024 * 1024;
jstring java_string(JNIEnv *env, const std::string &value) {
    // JNI's NewStringUTF expects modified UTF-8, not archive UTF-8 (e.g. emoji).
    auto utf16 = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.from_bytes(value);
    return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
}
void fail(JNIEnv *env, const std::string &message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message.c_str());
}
struct Reader {
    archive *handle = archive_read_new();
    int fd;
    explicit Reader(int source) : fd(dup(source)) {
        if (fd < 0 || lseek(fd, 0, SEEK_SET) < 0) {
            archive_read_free(handle); handle = nullptr; if (fd >= 0) close(fd); fd = -1;
            throw std::runtime_error("Archive source must be seekable.");
        }
        archive_read_support_filter_none(handle);
        archive_read_support_format_rar(handle);
        archive_read_support_format_rar5(handle);
        if (archive_read_open_fd(handle, fd, 65536) != ARCHIVE_OK) {
            std::string error = archive_error_string(handle) ? archive_error_string(handle) : "Cannot open this RAR archive.";
            archive_read_free(handle); handle = nullptr; close(fd); fd = -1;
            throw std::runtime_error(error);
        }
    }
    ~Reader() { if (handle) archive_read_free(handle); if (fd >= 0) close(fd); }
};
std::string safe_name(archive_entry *entry) {
    const char *utf = archive_entry_pathname_utf8(entry);
    const char *raw = utf ? utf : archive_entry_pathname(entry);
    std::string name = raw ? raw : "";
    if (name.empty() || name.size() > 2048 || name[0] == '/' || name[0] == '\\' || name.find(':') != std::string::npos)
        throw std::runtime_error("Archive contains an unsafe path.");
    for (char &c : name) { if (static_cast<unsigned char>(c) < 32) throw std::runtime_error("Invalid archive filename."); if (c == '\\') c = '/'; }
    if (name == ".." || name.rfind("../", 0) == 0 || name.find("/../") != std::string::npos ||
        (name.size() >= 3 && name.substr(name.size() - 3) == "/..")) throw std::runtime_error("Unsafe archive path.");
    if (archive_entry_is_encrypted(entry) == 1) throw std::runtime_error("Encrypted archives are not supported.");
    if (archive_entry_symlink(entry) || archive_entry_hardlink(entry)) throw std::runtime_error("Archive links are not supported.");
    return name;
}
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_dev_soto_panelgrove_engine_NativeArchive_list(JNIEnv *env, jobject, jint fd) {
    try {
        Reader reader(fd); std::vector<std::string> names; archive_entry *entry; int status; int64_t total = 0; int count = 0; size_t name_bytes = 0;
        while ((status = archive_read_next_header(reader.handle, &entry)) == ARCHIVE_OK || status == ARCHIVE_WARN) {
            if (++count > MAX_ENTRIES) throw std::runtime_error("Archive contains too many entries.");
            std::string name = safe_name(entry);
            name_bytes += name.size(); if (name_bytes > 256 * 1024) throw std::runtime_error("Archive filename index exceeds the supported size.");
            int64_t size = archive_entry_size(entry);
            if (size < 0 || size > 128LL * 1024 * 1024 || total > MAX_EXPANDED - size)
                throw std::runtime_error("Archive exceeds extraction limits.");
            total += size;
            if (archive_entry_filetype(entry) == AE_IFREG) names.push_back(name);
            if (archive_read_data_skip(reader.handle) < ARCHIVE_WARN) throw std::runtime_error("Cannot index this RAR variant.");
        }
        if (status != ARCHIVE_EOF) throw std::runtime_error(archive_error_string(reader.handle) ?: "Invalid RAR archive.");
        jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), env->FindClass("java/lang/String"), nullptr);
        for (size_t i = 0; i < names.size(); ++i) { jstring s = java_string(env, names[i]); if (!s) return nullptr; env->SetObjectArrayElement(result, i, s); env->DeleteLocalRef(s); }
        return result;
    } catch (const std::exception &e) { fail(env, e.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_soto_panelgrove_engine_NativeArchive_extract(JNIEnv *env, jobject, jint fd, jstring wanted, jint output, jlong limit) {
    try {
        const jchar *chars = env->GetStringChars(wanted, nullptr);
        if (!chars) return;
        std::u16string utf16(reinterpret_cast<const char16_t *>(chars), env->GetStringLength(wanted));
        env->ReleaseStringChars(wanted, chars);
        std::string target = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>{}.to_bytes(utf16);
        Reader reader(fd); archive_entry *entry; int count = 0; int status;
        while ((status = archive_read_next_header(reader.handle, &entry)) == ARCHIVE_OK || status == ARCHIVE_WARN) {
            if (++count > MAX_ENTRIES) throw std::runtime_error("Archive contains too many entries.");
            std::string name = safe_name(entry);
            if (name == target) {
                if (archive_entry_filetype(entry) != AE_IFREG) throw std::runtime_error("Page is not a regular file.");
                char buffer[65536]; int64_t bytes = 0; la_ssize_t n;
                while ((n = archive_read_data(reader.handle, buffer, sizeof(buffer))) > 0) {
                    bytes += n;
                    if (bytes > limit || bytes > 128LL * 1024 * 1024) throw std::runtime_error("Page exceeds extraction limit.");
                    la_ssize_t written = 0;
                    while (written < n) { ssize_t w = write(output, buffer + written, n - written); if (w <= 0) throw std::runtime_error("Not enough space to prepare this page."); written += w; }
                }
                if (n < 0) throw std::runtime_error(archive_error_string(reader.handle) ?: "Corrupt archive page.");
                return;
            }
            if (archive_read_data_skip(reader.handle) < ARCHIVE_WARN) throw std::runtime_error("Cannot read this RAR variant.");
        }
        if (status != ARCHIVE_EOF) throw std::runtime_error(archive_error_string(reader.handle) ?: "Invalid RAR archive.");
        throw std::runtime_error("Page is missing from this archive.");
    } catch (const std::exception &e) { fail(env, e.what()); }
}
