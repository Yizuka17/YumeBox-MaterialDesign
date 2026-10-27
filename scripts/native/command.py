"""Process execution helpers used by the native build pipeline."""

from __future__ import annotations

from dataclasses import dataclass
import os
from pathlib import Path
import shutil
import subprocess
from threading import Thread
from typing import Iterable, Mapping


@dataclass
class CommandResult:
    success: bool
    output: str = ""
    error: str = ""


def command_exists(command: str) -> bool:
    return shutil.which(command) is not None


def execute_command(
    command: Iterable[str],
    working_dir: Path | None = None,
    environment: Mapping[str, str] | None = None,
    *,
    print_stdout: bool = True,
    print_stderr: bool = True,
    stderr_is_error: bool = True,
    stdout_prefix: str | None = "[cmd]",
    stderr_prefix: str | None = None,
) -> CommandResult:
    """Run a command while preserving live stdout/stderr output."""

    args = [str(item) for item in command]
    if stderr_prefix is None:
        stderr_prefix = "[err]" if stderr_is_error else "[cmd]"

    env = os.environ.copy()
    if environment:
        env.update({key: str(value) for key, value in environment.items()})

    try:
        process = subprocess.Popen(
            args,
            cwd=working_dir,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
            bufsize=1,
        )
    except OSError as error:
        return CommandResult(False, error=str(error))

    output: list[str] = []
    errors: list[str] = []

    def consume(stream, sink: list[str], enabled: bool, prefix: str | None) -> None:
        for line in iter(stream.readline, ""):
            line = line.rstrip("\r\n")
            sink.append(line)
            if enabled:
                print(f"{prefix} {line}" if prefix else line, flush=True)
        stream.close()

    stdout_thread = Thread(
        target=consume,
        args=(process.stdout, output, print_stdout, stdout_prefix),
        daemon=True,
    )
    stderr_thread = Thread(
        target=consume,
        args=(process.stderr, errors, print_stderr, stderr_prefix),
        daemon=True,
    )
    stdout_thread.start()
    stderr_thread.start()
    exit_code = process.wait()
    stdout_thread.join()
    stderr_thread.join()
    return CommandResult(exit_code == 0, "\n".join(output), "\n".join(errors))


def command_failure(result: CommandResult) -> str:
    return (result.error or result.output).strip()

