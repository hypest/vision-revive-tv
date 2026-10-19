#!/usr/bin/env python3
import os
import glob
import subprocess
import sys

# Detect Android SDK
SDK_DIR = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Library/Android/sdk")
BUILD_TOOLS = os.path.join(SDK_DIR, "build-tools/35.0.0")
if not os.path.isdir(BUILD_TOOLS):
    # Try to find highest version available in build-tools
    available = glob.glob(os.path.join(SDK_DIR, "build-tools/*"))
    if available:
        BUILD_TOOLS = sorted(available)[-1]
    else:
        print(f"Error: No build-tools found in {SDK_DIR}/build-tools", file=sys.stderr)
        sys.exit(1)

PLATFORM_JAR = os.path.join(SDK_DIR, "platforms/android-28/android.jar")
if not os.path.exists(PLATFORM_JAR):
    available_platforms = glob.glob(os.path.join(SDK_DIR, "platforms/android-*"))
    if available_platforms:
        PLATFORM_JAR = os.path.join(sorted(available_platforms)[-1], "android.jar")
    else:
        print(f"Error: android.jar not found in {SDK_DIR}/platforms", file=sys.stderr)
        sys.exit(1)

KEYSTORE = os.path.expanduser("~/.android/debug.keystore")
OUTPUT_APK = "vision-revive-tv.apk"

def run(cmd, cwd=None):
    print("Running:", " ".join(cmd))
    res = subprocess.run(cmd, check=True, cwd=cwd)
    return res

def clean_build_dir():
    os.makedirs("build/classes", exist_ok=True)
    # Remove old class files to avoid stale inner classes
    for root, dirs, files in os.walk("build/classes", topdown=False):
        for f in files:
            os.remove(os.path.join(root, f))
        for d in dirs:
            os.rmdir(os.path.join(root, d))
    os.makedirs("build/classes", exist_ok=True)

clean_build_dir()

# 1. Compile Java
run([
    "javac", "-d", "build/classes",
    "-cp", PLATFORM_JAR,
    "-source", "1.8", "-target", "1.8",
    "src/org/hypest/visionrevivetv/MainActivity.java"
])

# 2. Dex classes
class_files = glob.glob("build/classes/org/hypest/visionrevivetv/*.class")
run([os.path.join(BUILD_TOOLS, "d8"), "--output", "build"] + class_files)

# 3. Package resources with aapt
unaligned_apk = "build/unaligned.apk"
if os.path.exists(unaligned_apk):
    os.remove(unaligned_apk)

run([
    os.path.join(BUILD_TOOLS, "aapt"), "package", "-f",
    "-M", "AndroidManifest.xml",
    "-S", "res",
    "-I", PLATFORM_JAR,
    "-F", unaligned_apk
])

# 4. Add classes.dex to APK
run([
    os.path.join(BUILD_TOOLS, "aapt"), "add",
    os.path.abspath(unaligned_apk),
    "classes.dex"
], cwd="build")

# 5. Zipalign
aligned_apk = "build/aligned.apk"
if os.path.exists(aligned_apk):
    os.remove(aligned_apk)

run([
    os.path.join(BUILD_TOOLS, "zipalign"), "-p", "-f", "4",
    unaligned_apk, aligned_apk
])

# 6. Sign APK
if os.path.exists(OUTPUT_APK):
    os.remove(OUTPUT_APK)

run([
    os.path.join(BUILD_TOOLS, "apksigner"), "sign",
    "--ks", KEYSTORE,
    "--ks-pass", "pass:android",
    "--out", OUTPUT_APK,
    aligned_apk
])

print(f"\nBUILD COMPLETE: {OUTPUT_APK} is ready!")
