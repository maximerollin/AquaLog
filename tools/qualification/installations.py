"""Run only on the reserved Pixel_9a API36. Never clears data or manages emulator lifecycle."""
import argparse
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
parser.add_argument("--run-id", required=True, help="Unique letters/digits/dashes; never resets an existing database")
args = parser.parse_args()
if not re.fullmatch(r"[A-Za-z0-9-]{1,48}", args.run_id):
    parser.error("Invalid run ID")


def adb(*arguments, input=None):
    return subprocess.run([args.adb, "-s", args.serial, *arguments], input=input,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout


if adb("shell", "getprop", "ro.build.version.sdk").strip() != b"36":
    raise RuntimeError("API36 required")
avd_name = adb("emu", "avd", "name").decode().splitlines()[0].strip()
if avd_name != "Pixel_9a":
    raise RuntimeError("Reserved Pixel_9a required")
subprocess.run([str(ROOT / "gradlew"), ":androidApp:assembleDebug", ":androidApp:assembleDebugAndroidTest"], cwd=ROOT, check=True)
package = "com.maximerollin.aqualog"
adb("install", "-r", str(ROOT / "androidApp/build/outputs/apk/debug/androidApp-debug.apk"))
adb("install", "-r", str(ROOT / "androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk"))


def phase(copy, value):
    output = adb("shell", "am", "instrument", "-w", "-e", "class",
                 "com.maximerollin.aqualog.InstallationQualificationTest", "-e",
                 "runInstallationQualification", "true", "-e", "qualificationPhase", value,
                 "-e", "qualificationCopy", copy, "-e", "qualificationRunId", args.run_id,
                 f"{package}.test/com.maximerollin.aqualog.AquaLogTestRunner").decode()
    print(output)
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE) or "FAILURES" in output:
        raise RuntimeError(f"{package} {value} failed")


phase("source", "seed")
adb("shell", "am", "force-stop", package)
phase("source", "verify")
payload = adb("exec-out", "run-as", package, "cat", "files/qualification-19-export.json")
adb("shell", "run-as", package, "sh", "-c", "'cat > files/qualification-19-import.json'", input=payload)
phase("destination", "import")
adb("shell", "am", "force-stop", package)
phase("destination", "verify")
print("PASS: process persistence and portable import into a fresh database. This is not a two-device proof.")
