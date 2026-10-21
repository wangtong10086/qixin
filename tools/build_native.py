"""Build artifacts only. WSL can reuse the installed Windows NDK."""
import os
from pathlib import Path
import subprocess

project = Path(__file__).resolve().parents[1]
ndk = Path(os.environ["ANDROID_NDK_HOME"])
output = project / "app/src/main/jniLibs/arm64-v8a"
output.mkdir(parents=True, exist_ok=True)
(project / "app/build").mkdir(parents=True, exist_ok=True)
(project / "app/src/main/assets").mkdir(parents=True, exist_ok=True)
clang = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android35-clang"
args = ["-shared", "-fPIC", "-O2", "-Wall", "-Wextra", "-Wno-unused-parameter", "-Wl,-z,max-page-size=16384"]
if clang.exists():
    source, target = str(project / "native/controller.c"), str(output / "libqixin.so")
else:
    clang = ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin/clang.exe"
    args.insert(0, "--target=aarch64-linux-android35")
    def win(path):
        return subprocess.check_output(["wslpath", "-w", str(path)], text=True).strip()
    source, target = win(project / "native/controller.c"), win(output / "libqixin.so")
subprocess.run([str(clang), *args, source, "-o", target], check=True)
