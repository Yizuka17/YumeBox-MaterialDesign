"""CMake builders for the PIE shell, loader and compatibility bridge."""

from __future__ import annotations

from .command import command_failure, execute_command
from .config import NdkTools, ProjectConfig, configured_abis


class CMakeBuilder:
    def __init__(
        self,
        config: ProjectConfig,
        ndk_tools: NdkTools,
        *,
        name: str,
        source_relative: str,
        output_relative: str,
        target: str | tuple[str, ...],
        output_names: tuple[str, ...],
        output_kind: str,
    ):
        self.config = config
        self.ndk_tools = ndk_tools
        self.root = config.project_root
        self.name = name
        self.source_dir = self.root / source_relative
        self.output_dir = self.root / output_relative
        self.targets = (target,) if isinstance(target, str) else target
        self.output_names = output_names
        self.output_kind = output_kind

    def build_all(self) -> None:
        cmake_file = self.source_dir / "CMakeLists.txt"
        if not cmake_file.is_file():
            raise RuntimeError(f"[{self.name}] Source directory not ready: missing {cmake_file}")
        abis = configured_abis(self.config)
        print(f"[{self.name}] Building for ABIs: {', '.join(abis)}")
        for abi in abis:
            self.build_for_abi(abi)

    def build_for_abi(self, abi: str) -> None:
        print(f"[building] Building for {abi} ({self.name} C)...")
        obj_dir = self.output_dir / "obj" / abi
        binary_dir = self.output_dir / abi
        obj_dir.mkdir(parents=True, exist_ok=True)
        binary_dir.mkdir(parents=True, exist_ok=True)
        toolchain = self.ndk_tools.ndk_dir / "build/cmake/android.toolchain.cmake"
        output_flag = "CMAKE_RUNTIME_OUTPUT_DIRECTORY" if self.output_kind == "runtime" else "CMAKE_LIBRARY_OUTPUT_DIRECTORY"
        configure = execute_command(
            [
                str(self.ndk_tools.get_cmake_path()), "-S", str(self.source_dir), "-B", str(obj_dir), "-G", "Ninja",
                f"-DCMAKE_MAKE_PROGRAM={self.ndk_tools.get_ninja_path()}",
                f"-DCMAKE_TOOLCHAIN_FILE={toolchain}", f"-DANDROID_ABI={abi}",
                f"-DANDROID_PLATFORM=android-{self.ndk_tools.get_min_android_api()}",
                "-DCMAKE_BUILD_TYPE=Release", f"-D{output_flag}={binary_dir}",
            ],
            stdout_prefix=f"[building][{abi}]",
            stderr_prefix=f"[building][{abi}]",
            stderr_is_error=False,
        )
        if not configure.success:
            raise RuntimeError(f"[{self.name}] Failed to configure {abi}: {command_failure(configure)}")
        build = execute_command(
            [str(self.ndk_tools.get_cmake_path()), "--build", str(obj_dir), "--target", *self.targets],
            stdout_prefix=f"[building][{abi}]",
            stderr_prefix=f"[building][{abi}]",
            stderr_is_error=False,
        )
        if not build.success:
            raise RuntimeError(f"[{self.name}] Failed to build {abi}: {command_failure(build)}")
        for output_name in self.output_names:
            source = binary_dir / output_name
            if not source.is_file():
                raise RuntimeError(f"[{self.name}] Output not found: {source}")
            destination = self.root / "jniLibs" / abi / output_name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read_bytes())
            print(f"[{self.name}] Copied to {destination}")


def shell_builder(config: ProjectConfig, ndk_tools: NdkTools) -> CMakeBuilder:
    return CMakeBuilder(config, ndk_tools, name="CoreShell", source_relative="lib/native/shell", output_relative="build/native/core-shell", target=("mihomo-shell", "mihomo-preview-shell"), output_names=("libmihomo.so", "libpreview.so"), output_kind="runtime")


def loader_builder(config: ProjectConfig, ndk_tools: NdkTools) -> CMakeBuilder:
    return CMakeBuilder(config, ndk_tools, name="Loader", source_relative="pack/native", output_relative="build/native/loader", target="loader", output_names=("libloader.so",), output_kind="library")


def compat_builder(config: ProjectConfig, ndk_tools: NdkTools) -> CMakeBuilder:
    return CMakeBuilder(config, ndk_tools, name="Compat", source_relative="lib/native/compat", output_relative="build/native/compat", target="compat", output_names=("libcompat.so",), output_kind="library")
