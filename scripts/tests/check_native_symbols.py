"""Check JNI exports before packaging; source namespace must match the native payloads."""
import os
import pathlib
import subprocess

root = pathlib.Path(__file__).resolve().parents[2]
sdk = pathlib.Path(os.environ['ANDROID_HOME'])
ndk = sdk / 'ndk' / os.environ.get('ANDROID_NDKVERSION', '30.0.16248370')
nm = ndk / 'toolchains/llvm/prebuilt' / ('windows-x86_64/bin/llvm-nm.exe' if os.name == 'nt' else 'linux-x86_64/bin/llvm-nm')
required = {
    'libloader.so': ['loader_NativePayloadExtractor_' + name for name in ['openArchive', 'extract', 'closeArchive']],
    'libcompat.so': ['core_bridge_NativeProcess_nativeStart', 'core_bridge_NativeProcess_nativeTerminate',
        'core_bridge_NativeProcess_nativeKill', 'core_bridge_Channel_nativeReadMessage',
        'core_bridge_Channel_nativeWriteMessage', 'core_bridge_UnixSocket_nativeConnectUnixSocket',
        'core_bridge_UnixSocket_nativeSetSoTimeout', 'core_bridge_UnixSocket_nativeGetSoTimeout'],
    'liboverride.so': ['core_bridge_Compiler_nativeGenAgeKey', 'core_bridge_Compiler_nativeAgePublicKey',
        'core_bridge_Compiler_nativeCompile'],
}
for name, suffixes in required.items():
    binary = root / 'jniLibs/arm64-v8a' / name
    output = subprocess.check_output([str(nm), '-D', '--defined-only', str(binary)], text=True)
    symbols = {line.split()[-1] for line in output.splitlines() if line.split()}
    for suffix in suffixes:
        expected = 'Java_com_github_yumelira_yumebox_' + suffix
        assert expected in symbols, f'{name}: missing {expected}'
    print(f'PASS: {name}: {len(suffixes)} JNI exports')
