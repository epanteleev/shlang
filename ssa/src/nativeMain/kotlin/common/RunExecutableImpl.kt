package common

import kotlinx.cinterop.*
import platform.posix.*


@OptIn(ExperimentalForeignApi::class)
actual fun runCommand(command: String, args: List<String>, workingDir: String?): ExecutionResult {
    val arena = Arena()

    val pipe = arena.allocArray<IntVar>(2)
    pipe(pipe)
    val pid = fork()
    if (pid == 0) {
        close(pipe[0])
        dup2(pipe[1], STDOUT_FILENO)
        dup2(pipe[1], STDERR_FILENO)
        close(pipe[1])
        val argv = arena.allocArray<CPointerVar<ByteVar>>(args.size + 2)
        argv[0] = command.cstr.getPointer(arena)
        for (i in args.indices) {
            argv[i + 1] = args[i].cstr.getPointer(arena)
        }
        argv[args.size + 1] = null
        val envp = null
        chdir(workingDir)
        execve(command, argv, envp)
        perror("execve")
        exit(1)
    }
    while (true) {
        val wpid = waitpid(pid, null, WNOHANG)
        if (wpid == pid) {
            break
        }
    }
    close(pipe[1])
    val output = buildString {
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(pipe[0], buffer.refTo(0), buffer.size.convert())
            if (count <= 0) {
                break
            }
            append(buffer.decodeToString(0, count.toInt()))
        }
    }

    close(pipe[0])
    return ExecutionResult(output, "", 0)
}

@OptIn(ExperimentalForeignApi::class)
actual fun env(name: String): String? {
    val env = getenv(name) ?: return null
    return env.toString()
}

@OptIn(ExperimentalForeignApi::class)
actual fun pwd(): String {
    val buffer = ByteArray(4096)
    getcwd(buffer.refTo(0), buffer.size.convert())
    return buffer.toKString()
}