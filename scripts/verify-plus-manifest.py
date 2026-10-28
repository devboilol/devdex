#!/usr/bin/env python3
"""Verify the merged manifest before publishing a DevDex Plus APK."""

from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_PACKAGE = "com.devboi.dex"
FORBIDDEN_HOME_SUFFIXES = (
    ".activities.LauncherActivity",
    ".activities.SecondaryLauncherActivity",
)


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: verify-plus-manifest.py MERGED_ANDROID_MANIFEST.xml", file=sys.stderr)
        return 2

    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"ERROR: manifest does not exist: {path}", file=sys.stderr)
        return 2

    root = ET.parse(path).getroot()
    package = root.attrib.get("package")
    if package != EXPECTED_PACKAGE:
        raise SystemExit(
            f"ERROR: expected application ID {EXPECTED_PACKAGE!r}, got {package!r}"
        )

    application = root.find("application")
    if application is None:
        raise SystemExit("ERROR: merged manifest has no <application> element")

    home_owners = []
    for component in list(application):
        if component.tag not in {"activity", "activity-alias"}:
            continue
        component_name = component.attrib.get(ANDROID + "name", "<unnamed>")
        for intent_filter in component.findall("intent-filter"):
            actions = {
                node.attrib.get(ANDROID + "name")
                for node in intent_filter.findall("action")
            }
            categories = {
                node.attrib.get(ANDROID + "name")
                for node in intent_filter.findall("category")
            }
            if (
                "android.intent.action.MAIN" in actions
                and "android.intent.category.HOME" in categories
            ):
                home_owners.append(component_name)

    if len(home_owners) != 1:
        raise SystemExit(
            "ERROR: expected exactly one MAIN/HOME activity, found "
            f"{len(home_owners)}: {home_owners}"
        )

    home = home_owners[0]
    if any(home.endswith(suffix) for suffix in FORBIDDEN_HOME_SUFFIXES):
        raise SystemExit(
            f"ERROR: legacy DevDex home activity still owns HOME: {home}"
        )

    print(f"Manifest verified: package={package}; HOME owner={home}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
