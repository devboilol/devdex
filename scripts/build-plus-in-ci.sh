#!/usr/bin/env bash
set -euo pipefail

DEVDEX_DIR="$1"
LAWNCHAIR_DIR="$2"
test -n "$DEVDEX_DIR" && test -n "$LAWNCHAIR_DIR"
cd "$LAWNCHAIR_DIR"

echo "== Apply Lawnchair P0 freeform launch integration =="
python3 "$DEVDEX_DIR/scripts/apply-plus-p0.py" "$LAWNCHAIR_DIR"

echo "== Import DevDex engine source as an Android library =="
rm -rf devdexengine
mkdir -p devdexengine/src/main
cp -a "$DEVDEX_DIR/app/src/main/." devdexengine/src/main/
cat > devdexengine/build.gradle <<'GRADLE'
plugins {
    id 'com.android.library'
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace "com.youki.dex"
    buildFeatures {
        buildConfig true
    }
    defaultConfig {
        minSdk 26
    }
}

configurations.configureEach {
    // Lawnchair ships the modern Parcelize runtime; one legacy DevDex transitive
    // dependency still pulls the obsolete Android Extensions runtime with the same classes.
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-android-extensions-runtime'
}

dependencies {
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0'
    implementation 'androidx.appcompat:appcompat:1.7.1'
    implementation 'com.google.android.material:material:1.14.0-alpha10'
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
    implementation 'androidx.preference:preference-ktx:1.2.1'
    implementation 'androidx.viewpager2:viewpager2:1.1.0'
    implementation 'com.jaredrummler:colorpicker:1.1.0'
    implementation 'com.github.ChickenHook:RestrictionBypass:2.2'
    implementation 'dev.rikka.shizuku:api:13.1.5'
    implementation 'dev.rikka.shizuku:provider:13.1.5'
    implementation 'com.google.android.gms:play-services-cast-framework:21.4.0'
    implementation 'androidx.mediarouter:mediarouter:1.7.0'
    implementation 'androidx.media3:media3-exoplayer:1.6.1'
    implementation 'androidx.media3:media3-common:1.6.1'
    implementation 'androidx.media3:media3-ui:1.6.1'
    implementation 'io.coil-kt:coil:2.7.0'
    implementation 'io.coil-kt:coil-video:2.7.0'
    implementation 'androidx.room:room-runtime:2.6.1'
    implementation 'androidx.room:room-ktx:2.6.1'
    implementation 'androidx.lifecycle:lifecycle-service:2.8.6'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.8.6'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'org.jsoup:jsoup:1.17.2'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3'
}
GRADLE

python3 - "$LAWNCHAIR_DIR" <<'PY'
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1])
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
ET.register_namespace("tools", "http://schemas.android.com/tools")
ANDROID = "{http://schemas.android.com/apk/res/android}"

settings = root / "settings.gradle"
text = settings.read_text()
if "include ':devdexengine'" not in text:
    text += "\n// DevDex Plus integration module\ninclude ':devdexengine'\n"
settings.write_text(text)

build = root / "build.gradle"
text = build.read_text()
if "implementation project(':devdexengine')" not in text:
    needle = "addFrameworkJar('framework-16.jar')\n\ndependencies {\n"
    insert = (needle
        + "    implementation project(':devdexengine')\n"
        + "    implementation 'dev.rikka.shizuku:api:13.1.5'\n"
        + "    implementation 'dev.rikka.shizuku:provider:13.1.5'\n")
    if needle not in text:
        raise SystemExit("Could not find Lawnchair application dependency block")
    text = text.replace(needle, insert, 1)
text = text.replace("applicationId 'app.lawnchair'", "applicationId 'com.devboi.dex'")
text = text.replace('applicationIdSuffix ".debug"', '// DevDex Plus: use one package ID for this alpha build.')
text = text.replace('resValue("string", "derived_app_name", "Lawnchair (Debug)")',
                    'resValue("string", "derived_app_name", "DevDex Plus (Debug)")')
text = text.replace('resValue("string", "derived_app_name", "Lawnchair")',
                    'resValue("string", "derived_app_name", "DevDex Plus")')
text = text.replace('output.outputFileName.set("Lawnchair.', 'output.outputFileName.set("DevDexPlus.')

# Exclude the legacy Kotlin Android Extensions runtime at the APK's dependency
# resolution level. The conflicting module can arrive from transitive dependencies
# other than DevDex, so excluding only inside devdexengine is insufficient.
# Exclude old Kotlin Android Extensions runtime in every Android/utility project.
# The APK is assembled from a multi-project graph, so apply the rule to all projects,
# not only the application project and not only DevDex's library module.
exclude_rule = """allprojects {
    configurations.configureEach {
        exclude group: 'org.jetbrains.kotlin', module: 'kotlin-android-extensions-runtime'
    }
}

"""
if "exclude group: 'org.jetbrains.kotlin', module: 'kotlin-android-extensions-runtime'" not in text:
    needle = "addFrameworkJar('framework-16.jar')\n\ndependencies {\n"
    if needle not in text:
        raise SystemExit("Could not find root dependency block for duplicate-runtime exclusion")
    text = text.replace(needle, "addFrameworkJar('framework-16.jar')\n\n" + exclude_rule + "dependencies {\n", 1)
build.write_text(text)

manifest = root / "devdexengine/src/main/AndroidManifest.xml"
tree = ET.parse(manifest)
doc = tree.getroot()
for app in doc.findall("application"):
    for attr in ("name", "label", "icon", "roundIcon", "theme", "allowBackup",
                 "requestLegacyExternalStorage", "localeConfig", "debuggable",
                 "fullBackupContent", "fullBackupOnly", "resizeableActivity"):
        app.attrib.pop(ANDROID + attr, None)

    # Lawnchair already uses AndroidX FileProvider for overview sharing. Keep DevDex's
    # own share paths by giving its provider class a distinct component name.
    for provider in app.findall("provider"):
        if provider.attrib.get(ANDROID + "name") == "androidx.core.content.FileProvider":
            provider.set(ANDROID + "name", ".utils.DevDexFileProvider")

    for activity in list(app.findall("activity")):
        for intent_filter in list(activity.findall("intent-filter")):
            actions = {node.attrib.get(ANDROID + "name") for node in intent_filter.findall("action")}
            categories = {node.attrib.get(ANDROID + "name") for node in intent_filter.findall("category")}
            if "android.intent.action.MAIN" in actions and categories.intersection({
                "android.intent.category.LAUNCHER",
                "android.intent.category.HOME",
                "android.intent.category.SECONDARY_HOME",
            }):
                activity.remove(intent_filter)
ET.indent(tree, space="    ")
tree.write(manifest, encoding="utf-8", xml_declaration=True)

# Avoid collision with Lawnchair's similarly named generic activity theme.
src = root / "devdexengine/src/main"
provider_src = src / "java/com/youki/dex/utils/DevDexFileProvider.kt"
provider_src.parent.mkdir(parents=True, exist_ok=True)
provider_src.write_text("""package com.youki.dex.utils

/**
 * Dedicated DevDex file provider; Lawnchair uses a separate AndroidX FileProvider
 * component and authority in the same APK.
 */
class DevDexFileProvider : androidx.core.content.FileProvider()
""", encoding="utf-8")

for path in src.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in {".xml", ".kt", ".java"}:
        continue
    try:
        data = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    updated = data.replace("AppTheme", "DevDexAppTheme")
    if updated != data:
        path.write_text(updated, encoding="utf-8")

# Shizuku multiprocess support must be enabled in Application construction, before providers.
app = root / "lawnchair/src/app/lawnchair/LawnchairApp.kt"
data = app.read_text(encoding="utf-8")
needle = "class LawnchairApp : LauncherApplication() {\n"
replacement = """class LawnchairApp : LauncherApplication() {
    // DevDex Plus: Shizuku must initialize multiprocess support before providers.
    init {
        rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(true)
    }

"""
if "ShizukuProvider.enableMultiProcessSupport(true)" not in data:
    if needle not in data:
        raise SystemExit("Could not find LawnchairApp class declaration")
    app.write_text(data.replace(needle, replacement, 1), encoding="utf-8")

print("Prepared Lawnchair + DevDex Plus source integration.")
PY

echo "== Integrated source changes =="
git status --short
