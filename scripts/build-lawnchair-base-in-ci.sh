#!/usr/bin/env bash
set -euo pipefail

LAWNCHAIR_DIR="${1:?Usage: build-lawnchair-base-in-ci.sh LAWNCHAIR_DIR}"
cd "$LAWNCHAIR_DIR"

echo "== Prepare standalone Lawnchair as DevDex Plus launcher base =="
python3 - "$LAWNCHAIR_DIR" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
build_file = root / "build.gradle"
text = build_file.read_text(encoding="utf-8")

if "applicationId 'app.lawnchair'" in text:
    text = text.replace("applicationId 'app.lawnchair'", "applicationId 'com.devboi.dex'")
elif "applicationId 'com.devboi.dex'" not in text:
    raise SystemExit("Could not identify Lawnchair github flavor applicationId")

text = text.replace(
    'applicationIdSuffix ".debug"',
    '// DevDex Plus launcher-base: keep the package ID stable for alpha testing.',
)

text = text.replace(
    'resValue("string", "derived_app_name", "Lawnchair (Debug)")',
    'resValue("string", "derived_app_name", "DevDex Plus Launcher Base (Debug)")',
)
text = text.replace(
    'resValue("string", "derived_app_name", "Lawnchair")',
    'resValue("string", "derived_app_name", "DevDex Plus Launcher Base")',
)
text = text.replace(
    'output.outputFileName.set("Lawnchair.',
    'output.outputFileName.set("DevDexPlus-LauncherBase.',
)

if "applicationId 'com.devboi.dex'" not in text:
    raise SystemExit("Package ID transformation failed")
if "DevDex Plus Launcher Base" not in text:
    raise SystemExit("Application-label transformation failed")
if 'output.outputFileName.set("DevDexPlus-LauncherBase.' not in text:
    raise SystemExit("APK filename transformation failed")

build_file.write_text(text, encoding="utf-8")
print("Prepared launcher-only baseline; DevDex engine is not included.")
PY
