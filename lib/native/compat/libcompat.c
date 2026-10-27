/*
 * This file is part of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 *
 */

/*
 * libcompat.so — JNI primitives for the non-root (VpnService) launch path, and nothing else:
 * fork+execve the PIE shell with a SOCK_SEQPACKET socketpair passed as CHANNEL=<fd>, signal it,
 * exchange one datagram per call (optionally carrying a descriptor via SCM_RIGHTS — how the TUN
 * fd reaches the core), and connect to the core's controller socket. SEQPACKET keeps message
 * boundaries, so the protocol needs no framing. Everything else runs over the mihomo REST API
 * from Kotlin; the root path launches through `su` and never loads this library.
 */

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <jni.h>

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/un.h>
#include <sys/wait.h>
#include <unistd.h>

// Datagrams up to this size are staged on the stack; larger ones fall back to malloc.
#define SCRATCH_SIZE 4096

extern char **environ;

static void throw_io(JNIEnv *env, const char *fmt, ...) {
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
    char message[256];
    va_list args;
    va_start(args, fmt);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);
    jclass exception = (*env)->FindClass(env, "java/io/IOException");
    if (exception != NULL) {
        (*env)->ThrowNew(env, exception, message);
    }
}

// ---- NativeProcess ----

static void free_argv(char **argv) {
    if (argv == NULL) {
        return;
    }
    for (char **it = argv; *it != NULL; it++) {
        free(*it);
    }
    free(argv);
}

// argv = [path, args..., NULL], strdup'd so it outlives the JNI local refs and stays valid in the
// forked child up to execve.
static char **build_argv(JNIEnv *env, const char *path, jobjectArray args) {
    jsize count = (*env)->GetArrayLength(env, args);
    char **argv = calloc((size_t)count + 2, sizeof(char *));
    if (argv == NULL) {
        return NULL;
    }
    argv[0] = strdup(path);
    if (argv[0] == NULL) {
        free_argv(argv);
        return NULL;
    }
    for (jsize i = 0; i < count; i++) {
        jstring element = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        const char *chars = (*env)->GetStringUTFChars(env, element, NULL);
        if (chars != NULL) {
            argv[i + 1] = strdup(chars);
            (*env)->ReleaseStringUTFChars(env, element, chars);
        }
        (*env)->DeleteLocalRef(env, element);
        // A hole would terminate argv early and exec the core with silently missing flags.
        if (argv[i + 1] == NULL) {
            free_argv(argv);
            return NULL;
        }
    }
    return argv;
}

// envp = [environ..., "CHANNEL=<fd>", NULL]. Entries are borrowed, so a plain free() is enough.
static char **build_envp(char *channel_env) {
    size_t inherited = 0;
    while (environ[inherited] != NULL) {
        inherited++;
    }
    char **envp = calloc(inherited + 2, sizeof(char *));
    if (envp == NULL) {
        return NULL;
    }
    memcpy(envp, environ, inherited * sizeof(char *));
    envp[inherited] = channel_env;
    return envp;
}

// Nothing else waits on the core child — Kotlin only ever SIGKILLs it — so without this it stays a
// zombie until the app process dies. The thread blocks in waitpid and exits when the child does.
static void *reap_child(void *arg) {
    pid_t pid = (pid_t)(intptr_t)arg;
    while (waitpid(pid, NULL, 0) < 0 && errno == EINTR) {
        // retry
    }
    return NULL;
}

static void reap_detached(pid_t pid) {
    pthread_t thread;
    if (pthread_create(&thread, NULL, reap_child, (void *)(intptr_t)pid) == 0) {
        pthread_detach(thread);
    }
}

// err_fd is CLOEXEC, so a successful execve closes it silently and the parent sees EOF instead.
static _Noreturn void child_fail(int err_fd) {
    int code = errno;
    (void)write(err_fd, &code, sizeof(code));
    _exit(127);
}

// Hand the core a clean fd table: close every inherited descriptor except keep_a/keep_b. The walk
// is raw getdents64 rather than opendir/readdir because malloc after fork() in a multi-threaded
// process can deadlock on an arena lock another JVM thread happened to hold. (close_range would
// be one syscall, but it is off Android's app seccomp allowlist below API 34.)
static void close_inherited(int keep_a, int keep_b) {
    int dir_fd = open("/proc/self/fd", O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    if (dir_fd < 0) {
        return;
    }
    __attribute__((aligned(8))) char entries[2048];
    for (;;) {
        long got = syscall(__NR_getdents64, dir_fd, entries, sizeof(entries));
        if (got <= 0) {
            break;
        }
        for (long offset = 0; offset < got;) {
            const void *raw = entries + offset;
            const struct dirent64 *entry = raw;
            offset += entry->d_reclen;

            int fd = 0;
            const char *cursor = entry->d_name;
            for (; *cursor >= '0' && *cursor <= '9'; cursor++) {
                fd = fd * 10 + (*cursor - '0');
            }
            if (cursor == entry->d_name || *cursor != '\0') {
                continue; // "." and ".."
            }
            if (fd >= 3 && fd != keep_a && fd != keep_b && fd != dir_fd) {
                close(fd);
            }
        }
    }
    close(dir_fd);
}

// Child half of nativeStart: no JVM access from here on, only async-signal-safe work.
static _Noreturn void child_exec(const char *path, char **argv, char **envp, const char *workdir,
                                 int channel_fd, int err_fd) {
    int null_fd = open("/dev/null", O_RDWR | O_CLOEXEC);
    if (null_fd < 0 || chdir(workdir) != 0) {
        child_fail(err_fd);
    }

    // stdout/stderr go to <workdir>/core.log, with /dev/null as the fallback.
    dup2(null_fd, STDIN_FILENO);
    dup2(null_fd, STDOUT_FILENO);
    dup2(null_fd, STDERR_FILENO);
    int log_fd = open("core.log", O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (log_fd >= 0) {
        dup2(log_fd, STDOUT_FILENO);
        dup2(log_fd, STDERR_FILENO);
        if (log_fd > STDERR_FILENO) {
            close(log_fd);
        }
    }
    if (null_fd > STDERR_FILENO) {
        close(null_fd);
    }

    close_inherited(channel_fd, err_fd);
    execve(path, argv, envp);
    child_fail(err_fd);
}

/*
 * NativeProcess.nativeStart(path, args, workdir) -> int[]{ pid, parentChannelFd }
 * A CLOEXEC self-pipe carries the child's errno back, so a pre-exec failure throws here instead
 * of handing back an already-dead pid.
 */
JNIEXPORT jintArray JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeStart(
        JNIEnv *env, jclass clazz, jstring path_value, jobjectArray args_value,
        jstring workdir_value) {
    (void)clazz;
    const char *path = NULL;
    const char *workdir = NULL;
    char **argv = NULL;
    char **envp = NULL;
    int fds[2] = {-1, -1};
    int err[2] = {-1, -1};
    char channel_env[32];
    jintArray result = NULL;

    path = (*env)->GetStringUTFChars(env, path_value, NULL);
    if (path == NULL) {
        goto cleanup;
    }
    workdir = (*env)->GetStringUTFChars(env, workdir_value, NULL);
    if (workdir == NULL) {
        goto cleanup;
    }
    if (access(path, R_OK | X_OK) != 0) {
        throw_io(env, "access %s: %s", path, strerror(errno));
        goto cleanup;
    }
    if (socketpair(AF_UNIX, SOCK_SEQPACKET, 0, fds) < 0) {
        throw_io(env, "socketpair: %s", strerror(errno));
        goto cleanup;
    }
    if (pipe2(err, O_CLOEXEC) < 0) {
        throw_io(env, "pipe2: %s", strerror(errno));
        goto cleanup;
    }

    snprintf(channel_env, sizeof(channel_env), "CHANNEL=%d", fds[1]);
    argv = build_argv(env, path, args_value);
    envp = argv != NULL ? build_envp(channel_env) : NULL;
    if (envp == NULL) {
        throw_io(env, "allocate argv/envp");
        goto cleanup;
    }

    pid_t pid = fork();
    if (pid < 0) {
        throw_io(env, "fork: %s", strerror(errno));
        goto cleanup;
    }
    if (pid == 0) {
        child_exec(path, argv, envp, workdir, fds[1], err[1]);
    }

    // Parent: drop the child's ends, then wait for it to either exec (EOF) or report an errno.
    close(fds[1]);
    fds[1] = -1;
    close(err[1]);
    err[1] = -1;

    int child_errno = 0;
    ssize_t got;
    while ((got = read(err[0], &child_errno, sizeof(child_errno))) < 0 && errno == EINTR) {
        // retry
    }
    if (got == (ssize_t)sizeof(child_errno)) {
        // Child died before exec; reap it so it doesn't linger as a zombie, then surface why.
        while (waitpid(pid, NULL, 0) < 0 && errno == EINTR) {
            // retry
        }
        throw_io(env, "exec %s: %s", path, strerror(child_errno));
        goto cleanup;
    }

    reap_detached(pid);

    result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        // No handle to hand back, so nothing could ever stop it: an orphan core would keep the tun.
        kill(pid, SIGKILL);
        goto cleanup;
    }
    jint values[2] = {(jint)pid, (jint)fds[0]};
    (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    fds[0] = -1; // the channel fd now belongs to Kotlin

cleanup:
    for (int i = 0; i < 2; i++) {
        if (fds[i] >= 0) close(fds[i]);
        if (err[i] >= 0) close(err[i]);
    }
    free_argv(argv);
    free(envp);
    if (workdir != NULL) (*env)->ReleaseStringUTFChars(env, workdir_value, workdir);
    if (path != NULL) (*env)->ReleaseStringUTFChars(env, path_value, path);
    return result;
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeTerminate(
        JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    kill((pid_t)pid, SIGTERM);
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_NativeProcess_nativeKill(
        JNIEnv *env, jclass clazz, jint pid) {
    (void)env;
    (void)clazz;
    kill((pid_t)pid, SIGKILL);
}

// ---- Channel — one datagram per call; the slice is validated in Channel.requireSlice ----

// GetByteArrayElements is avoided deliberately: ART copies the *whole* array, so streaming a
// config in 32 KiB chunks would recopy all of it once per chunk.
static char *scratch_for(JNIEnv *env, jint length, char *scratch, size_t scratch_size) {
    if ((size_t)length <= scratch_size) {
        return scratch;
    }
    char *data = malloc((size_t)length);
    if (data == NULL) {
        throw_io(env, "allocate %d bytes", length);
    }
    return data;
}

/*
 * Channel.nativeReadMessage(fd, buf, off, len, fdHolder) -> bytes read (0 = EOF, -1 = error)
 * A descriptor the peer attached via SCM_RIGHTS lands in fdHolder[0], otherwise -1.
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Channel_nativeReadMessage(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint offset, jint length,
        jintArray fd_holder) {
    (void)clazz;
    char scratch[SCRATCH_SIZE];
    char *data = scratch_for(env, length, scratch, sizeof(scratch));
    if (data == NULL) {
        return -1;
    }

    struct iovec iov = {.iov_base = data, .iov_len = (size_t)length};
    union {
        struct cmsghdr align;
        char buf[CMSG_SPACE(sizeof(int))];
    } control;

    struct msghdr message;
    memset(&message, 0, sizeof(message));
    message.msg_iov = &iov;
    message.msg_iovlen = 1;
    message.msg_control = control.buf;
    message.msg_controllen = sizeof(control.buf);

    ssize_t count = recvmsg(fd, &message, 0);
    int saved_errno = errno;

    jint received_fd = -1;
    if (count > 0) {
        for (struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message); cmsg != NULL;
             cmsg = CMSG_NXTHDR(&message, cmsg)) {
            if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS &&
                cmsg->cmsg_len == CMSG_LEN(sizeof(int))) {
                memcpy(&received_fd, CMSG_DATA(cmsg), sizeof(int));
                break;
            }
        }
        (*env)->SetByteArrayRegion(env, buffer, offset, (jint)count, (const jbyte *)data);
    }
    if (data != scratch) {
        free(data);
    }

    // SEQPACKET silently discards what does not fit, and MSG_CTRUNC means the kernel closed a
    // descriptor it could not deliver; both would corrupt the protocol if reported as success.
    int truncated = (message.msg_flags & (MSG_TRUNC | MSG_CTRUNC)) != 0;
    // A rejected read throws, so its ReadResult never reaches the caller — an attached descriptor
    // would leak with nobody left to close it.
    if (truncated && received_fd >= 0) {
        close(received_fd);
        received_fd = -1;
    }
    (*env)->SetIntArrayRegion(env, fd_holder, 0, 1, &received_fd);

    if (count < 0) {
        throw_io(env, "recvmsg: %s", strerror(saved_errno));
        return -1;
    }
    if (truncated) {
        throw_io(env, "datagram truncated (flags=%#x, %d of %d bytes)", message.msg_flags,
                 (int)count, length);
        return -1;
    }
    return (jint)count;
}

/*
 * Channel.nativeWriteMessage(fd, buf, off, len, attachFd) -> bytes written (-1 = error)
 * attachFd >= 0 is passed to the peer via SCM_RIGHTS; -1 means no descriptor.
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_Channel_nativeWriteMessage(
        JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint offset, jint length,
        jint attach_fd) {
    (void)clazz;
    char scratch[SCRATCH_SIZE];
    char *data = scratch_for(env, length, scratch, sizeof(scratch));
    if (data == NULL) {
        return -1;
    }
    (*env)->GetByteArrayRegion(env, buffer, offset, length, (jbyte *)data);
    if ((*env)->ExceptionCheck(env)) {
        if (data != scratch) free(data);
        return -1;
    }

    struct iovec iov = {.iov_base = data, .iov_len = (size_t)length};
    union {
        struct cmsghdr align;
        char buf[CMSG_SPACE(sizeof(int))];
    } control;
    memset(&control, 0, sizeof(control));

    struct msghdr message;
    memset(&message, 0, sizeof(message));
    message.msg_iov = &iov;
    message.msg_iovlen = 1;

    if (attach_fd >= 0) {
        message.msg_control = control.buf;
        message.msg_controllen = sizeof(control.buf);
        struct cmsghdr *cmsg = CMSG_FIRSTHDR(&message);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        cmsg->cmsg_len = CMSG_LEN(sizeof(int));
        int raw = attach_fd;
        memcpy(CMSG_DATA(cmsg), &raw, sizeof(int));
    }

    ssize_t count = sendmsg(fd, &message, 0);
    int saved_errno = errno;
    if (data != scratch) {
        free(data);
    }

    if (count < 0) {
        throw_io(env, "sendmsg: %s", strerror(saved_errno));
        return -1;
    }
    return (jint)count;
}

// ---- UnixSocket — the mihomo external controller connection ----

/*
 * UnixSocket.nativeConnectUnixSocket(path, timeoutMs) -> connected SOCK_STREAM fd
 * A leading '@' selects the abstract namespace; timeoutMs <= 0 is a plain blocking connect.
 */
JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeConnectUnixSocket(
        JNIEnv *env, jclass clazz, jstring path_value, jint timeout_ms) {
    (void)clazz;
    const char *path = (*env)->GetStringUTFChars(env, path_value, NULL);
    if (path == NULL) {
        return -1;
    }

    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    // Truncating instead would connect to a different socket than the caller asked for.
    size_t path_len = strlen(path);
    int usable = path_len > 0 && path_len < sizeof(address.sun_path);
    if (usable) {
        memcpy(address.sun_path, path, path_len);
    }
    (*env)->ReleaseStringUTFChars(env, path_value, path);
    if (!usable) {
        throw_io(env, "controller path length %zu out of range", path_len);
        return -1;
    }

    socklen_t address_len = (socklen_t)(offsetof(struct sockaddr_un, sun_path) + path_len + 1);
    if (address.sun_path[0] == '@') {
        // Abstract namespace: first byte NUL, no trailing NUL in the length.
        address.sun_path[0] = '\0';
        address_len--;
    }

    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        throw_io(env, "socket: %s", strerror(errno));
        return -1;
    }

    int original_flags = -1;
    if (timeout_ms > 0) {
        original_flags = fcntl(fd, F_GETFL, 0);
        if (original_flags < 0 || fcntl(fd, F_SETFL, original_flags | O_NONBLOCK) < 0) {
            throw_io(env, "fcntl: %s", strerror(errno));
            close(fd);
            return -1;
        }
    }

    int connect_error = 0;
    if (connect(fd, (struct sockaddr *)&address, address_len) < 0) {
        connect_error = errno;
        if (timeout_ms > 0 && connect_error == EINPROGRESS) {
            struct pollfd poll_fd = {.fd = fd, .events = POLLOUT, .revents = 0};
            int poll_result;
            do {
                poll_result = poll(&poll_fd, 1, timeout_ms);
            } while (poll_result < 0 && errno == EINTR);

            if (poll_result == 0) {
                connect_error = ETIMEDOUT;
            } else if (poll_result < 0) {
                connect_error = errno;
            } else {
                socklen_t error_len = sizeof(connect_error);
                if (getsockopt(fd, SOL_SOCKET, SO_ERROR, &connect_error, &error_len) < 0) {
                    connect_error = errno;
                }
            }
        }
    }

    if (original_flags >= 0 && fcntl(fd, F_SETFL, original_flags) < 0 && connect_error == 0) {
        connect_error = errno;
    }
    if (connect_error != 0) {
        throw_io(env, "connect: %s", strerror(connect_error));
        close(fd);
        return -1;
    }
    return fd;
}

JNIEXPORT void JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeSetSoTimeout(
        JNIEnv *env, jclass clazz, jint fd, jint timeout_ms) {
    (void)clazz;
    struct timeval tv;
    tv.tv_sec = timeout_ms / 1000;
    tv.tv_usec = (timeout_ms % 1000) * 1000;
    if (setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0 ||
        setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) {
        throw_io(env, "setsockopt: %s", strerror(errno));
    }
}

JNIEXPORT jint JNICALL
Java_com_github_yumelira_yumebox_core_bridge_UnixSocket_nativeGetSoTimeout(
        JNIEnv *env, jclass clazz, jint fd) {
    (void)clazz;
    struct timeval tv;
    socklen_t len = sizeof(tv);
    if (getsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &tv, &len) < 0) {
        throw_io(env, "getsockopt: %s", strerror(errno));
        return -1;
    }
    return (jint)(tv.tv_sec * 1000 + tv.tv_usec / 1000);
}
