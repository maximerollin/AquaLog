"""Run only on the reserved Pixel_9a API36. Never clears data or manages emulator lifecycle."""
import argparse
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--adb", default="adb")
args = parser.parse_args()


def adb(*arguments, input=None):
    return subprocess.run([args.adb, "-s", args.serial, *arguments], input=input,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout


if adb("shell", "getprop", "ro.build.version.sdk").strip() != b"36":
    raise RuntimeError("API36 required")
avd_name = adb("emu", "avd", "name").decode().splitlines()[0].strip()
if avd_name != "Pixel_9a":
    raise RuntimeError("Reserved Pixel_9a required")
for profile in ("debug", "beta"):
    subprocess.run([str(ROOT / "gradlew"), f":androidApp:assemble{profile.title()}",
                    ":androidApp:assembleAndroidTest", f"-PaqualogTestBuildType={profile}"], cwd=ROOT, check=True)
    adb("install", "-r", str(ROOT / f"androidApp/build/outputs/apk/{profile}/androidApp-{profile}.apk"))
    adb("install", "-r", str(ROOT / f"androidApp/build/outputs/apk/androidTest/{profile}/androidApp-{profile}-androidTest.apk"))


def phase(package, value):
    output = adb("shell", "am", "instrument", "-w", "-e", "class",
                 "com.maximerollin.aqualog.InstallationQualificationTest", "-e",
                 "runInstallationQualification", "true", "-e", "qualificationPhase", value,
                 f"{package}.test/com.maximerollin.aqualog.AquaLogTestRunner").decode()
    print(output)
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE) or "FAILURES" in output:
        raise RuntimeError(f"{package} {value} failed")


dev = "com.maximerollin.aqualog.dev"
beta = "com.maximerollin.aqualog.beta"
phase(dev, "seed")
adb("shell", "am", "force-stop", dev)
phase(dev, "verify")
payload = adb("exec-out", "run-as", dev, "cat", "files/qualification-19-export.json")
adb("shell", "run-as", beta, "mkdir", "-p", "files")
adb("shell", "run-as", beta, "sh", "-c", "'cat > files/qualification-19-import.json'", input=payload)
phase(beta, "import")
adb("shell", "am", "force-stop", beta)
phase(beta, "verify")
print("PASS: repository/UI persisted through process stops and portable data crossed dev/beta sandboxes.")
