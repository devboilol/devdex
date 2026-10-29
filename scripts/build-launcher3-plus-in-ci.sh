#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ]; then
  echo "Usage: build-launcher3-plus-in-ci.sh DEVDEX_DIR LAUNCHER3_DIR" >&2
  exit 2
fi
DEVDEX_DIR="$1"
LAUNCHER3_DIR="$2"
test -d "$DEVDEX_DIR/app/src/main"
test -f "$LAUNCHER3_DIR/build.gradle"
cd "$LAUNCHER3_DIR"

echo "== Import DevDex Window Engine as an Android library =="
rm -rf devdexengine
mkdir -p devdexengine/src/main
cp -a "$DEVDEX_DIR/app/src/main/." devdexengine/src/main/

cat > devdexengine/build.gradle <<'GRADLE'
apply plugin: 'com.android.library'
apply plugin: 'org.jetbrains.kotlin.android'
apply plugin: 'kotlin-parcelize'
apply plugin: 'org.jetbrains.kotlin.plugin.serialization'

android {
    namespace "com.youki.dex"
    compileSdkVersion COMPILE_SDK
    buildFeatures { buildConfig true }
    defaultConfig { minSdkVersion 26 }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_21
        targetCompatibility JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = '21' }
}

configurations.configureEach {
    exclude group: 'org.jetbrains.kotlin', module: 'kotlin-android-extensions-runtime'
}

dependencies {
    compileOnly files(rootProject.file('prebuilts/libs/framework-16.jar'))
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

python3 - "$DEVDEX_DIR" "$LAUNCHER3_DIR" <<'PY'
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

devdex = Path(sys.argv[1])
root = Path(sys.argv[2])
android_ns = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
ET.register_namespace("tools", "http://schemas.android.com/tools")

# Include DevDex's engine as a local library module.
settings = root / "settings.gradle"
settings_text = settings.read_text(encoding="utf-8")
if "include ':devdexengine'" not in settings_text:
    settings.write_text(settings_text.rstrip() + "\n\n// DevDex Plus window engine\ninclude ':devdexengine'\n", encoding="utf-8")

# Build one installable application while retaining Launcher3's source namespace.
build = root / "build.gradle"
build_text = build.read_text(encoding="utf-8")
# Kotlin serialization is a separate Gradle plugin artifact, not bundled into kotlin-gradle-plugin.
serialization_classpath = "        classpath 'org.jetbrains.kotlin:kotlin-serialization:2.2.21'\n"
if serialization_classpath.strip() not in build_text:
    plugin_anchor = "        classpath KOTLIN\n"
    if plugin_anchor not in build_text:
        raise SystemExit("Could not find root Kotlin Gradle plugin classpath")
    build_text = build_text.replace(plugin_anchor, plugin_anchor + serialization_classpath, 1)
needle = "applicationId 'com.android.launcher3'"
if needle not in build_text:
    raise SystemExit("Expected Launcher3 applicationId declaration was not found")
build_text = build_text.replace(needle, "applicationId 'com.devboi.dex'")

// The pinned port appends framework-16.jar to Kotlin libraries by default. That
// leaves public android.jar classes first, masking hidden nested classes such as
// TextClock.ClockEventDelegate, WindowManager.ScreenshotSource and InternalInsetsInfo.
// Make the framework jar the first Kotlin compiler library, matching newer upstream.
framework_order_old = "libraries.from(files(frameworkJar))"
framework_order_new = "libraries.setFrom(files(frameworkJar, libraries))"
if framework_order_old in build_text:
    build_text = build_text.replace(framework_order_old, framework_order_new, 1)
elif framework_order_new not in build_text:
    raise SystemExit("Could not find Kotlin framework classpath ordering insertion point")


# The stock Android SDK android.jar omits hidden framework types required by
# Quickstep/KAPT (TextClock.ClockEventDelegate, ScreenshotSource, InternalInsetsInfo).
# Launcher3 defines addFrameworkJar() to prepend framework-16.jar ahead of android.jar
# for JavaCompile and KotlinCompile classpaths; invoke it for the combined app build.
framework_call = "addFrameworkJar('framework-16.jar')"
if framework_call not in build_text:
    framework_anchor = "dependencies {\n    implementation libs.androidx.appcompat\n"
    if framework_anchor not in build_text:
        raise SystemExit("Could not find root AndroidX dependency block for framework classpath setup")
    build_text = build_text.replace(
        framework_anchor,
        "// DevDex Plus: expose Android 16 hidden framework APIs to Launcher3/Quickstep.\n"
        + framework_call + "\n\n" + framework_anchor,
        1,
    )

# The wm_shared source module depends on non-SDK framework internals and cannot
# be compiled by this standalone Gradle build. The upstream root build already
# includes its shared and Kotlin prebuilts, so do not add the overlapping
# desktopMode/AOSP variant jars or compile the WMShared source project.
build_text = build_text.replace(
    "    implementation project(':WMShared')",
    "",
)

if "implementation project(':devdexengine')" not in build_text:
    build_text += "\n\ndependencies {\n    implementation project(':devdexengine')\n    implementation 'dev.rikka.shizuku:api:13.1.5'\n    implementation 'dev.rikka.shizuku:provider:13.1.5'\n}\n"
build.write_text(build_text, encoding="utf-8")

# The Android 16 standalone port compiles platform-hidden APIs in module-local
# source sets. The root project's framework jar setup does not automatically
# reach library Kotlin tasks such as :flags, so wire it in explicitly.
flags_build = root / "flagslib/build.gradle"
flags_text = flags_build.read_text(encoding="utf-8")
dep_anchor = "    implementation(libs.annotation)\n"
if "framework-16.jar" not in flags_text:
    if dep_anchor not in flags_text:
        raise SystemExit("Could not find :flags dependency insertion point")
    flags_text = flags_text.replace(
        dep_anchor,
        dep_anchor + "    compileOnly files(rootProject.file('prebuilts/libs/framework-16.jar'))\n",
        1,
    )
task_anchor = "tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach"
if task_anchor not in flags_text:
    flags_text += """
\n// DevDex Plus: expose Android 16 hidden framework APIs to this Kotlin library.
tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
    libraries.from(files(rootProject.file('prebuilts/libs/framework-16.jar')))
}
tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach {
    classpath = files(rootProject.file('prebuilts/libs/framework-16.jar'), classpath)
}
"""
flags_build.write_text(flags_text, encoding="utf-8")

# IconLoader references generated Launcher3 Flags from :flags; the upstream
# Android 16 Gradle port omitted that module dependency.
icon_build = root / "iconloaderlib/build.gradle.kts"
icon_text = icon_build.read_text(encoding="utf-8")
icon_anchor = "    implementation(libs.annotation)\n"
if "implementation(project(\":flags\"))" not in icon_text:
    if icon_anchor not in icon_text:
        raise SystemExit("Could not find IconLoader dependency insertion point")
    icon_text = icon_text.replace(
        icon_anchor,
        icon_anchor + "    implementation(project(\":flags\"))\n",
        1,
    )
icon_build.write_text(icon_text, encoding="utf-8")

# The AOSP 16 source set contains two newer archive-icon flag references that
# are not defined by this branch's Android 16 flagslib. Preserve the Android 16
# behavior: do not activate the newer archive restore/cache path.
cache_logic = root / "iconloaderlib/src/com/android/launcher3/icons/cache/LauncherActivityCachingLogic.kt"
cache_logic_text = cache_logic.read_text(encoding="utf-8")
cache_logic_text = cache_logic_text.replace(
    "import com.android.launcher3.Flags.useNewIconForArchivedApps\n", ""
)
cache_logic.write_text(cache_logic_text, encoding="utf-8")

base_icon_cache = root / "iconloaderlib/src/com/android/launcher3/icons/cache/BaseIconCache.kt"
base_icon_cache_text = base_icon_cache.read_text(encoding="utf-8")
newer_archive_flag = "!(lookupFlags.skipAddToMemCache() && Flags.restoreArchivedAppIconsFromDb())"
if newer_archive_flag in base_icon_cache_text:
    base_icon_cache_text = base_icon_cache_text.replace(
        newer_archive_flag,
        "true /* Android 16: restoreArchivedAppIconsFromDb flag is not defined here. */",
        1,
    )
base_icon_cache.write_text(base_icon_cache_text, encoding="utf-8")

# Rename the launcher shown in Android's Home-app chooser.
strings = root / "res/values/strings.xml"
strings_text = strings.read_text(encoding="utf-8")
strings_text, count = re.subn(
    r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
    r'\1DevDex Plus\2', strings_text, count=1, flags=re.DOTALL
)
if count != 1:
    raise SystemExit("Could not find Launcher3 app_name resource")
strings.write_text(strings_text, encoding="utf-8")

# Keep Launcher3's Application initialization while registering Shizuku before providers.
common_manifest = root / "AndroidManifest-common.xml"
common_text = common_manifest.read_text(encoding="utf-8")
old_app = 'android:name="com.android.launcher3.LauncherApplication"'
new_app = 'android:name="com.youki.dex.DevDexPlusApplication"'
if new_app not in common_text:
    if old_app not in common_text:
        raise SystemExit("LauncherApplication manifest anchor not found")
    common_manifest.write_text(common_text.replace(old_app, new_app, 1), encoding="utf-8")

application_src = root / "src/com/youki/dex/DevDexPlusApplication.java"
application_src.parent.mkdir(parents=True, exist_ok=True)
application_src.write_text("""package com.youki.dex;

/** Launcher3 application bootstrap with Shizuku multiprocess support. */
public final class DevDexPlusApplication extends com.android.launcher3.LauncherApplication {
    public DevDexPlusApplication() {
        rikka.shizuku.ShizukuProvider.enableMultiProcessSupport(true);
    }
}
""", encoding="utf-8")

# Namespace DevDex styles so they cannot override Launcher3's AppTheme.
engine_src = root / "devdexengine/src/main"
for path in engine_src.rglob("*"):
    if not path.is_file() or path.suffix.lower() not in {".xml", ".kt", ".java"}:
        continue
    try:
        data = path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        continue
    updated = data.replace("AppTheme", "DevDexAppTheme")
    if updated != data:
        path.write_text(updated, encoding="utf-8")

# Merge DevDex components and permissions, but remove its Application attributes
# and HOME filters so Launcher3 remains the one Home owner.
engine_manifest = engine_src / "AndroidManifest.xml"
tree = ET.parse(engine_manifest)
manifest = tree.getroot()
for application in manifest.findall("application"):
    for attr in list(application.attrib):
        if attr.startswith(android_ns):
            application.attrib.pop(attr, None)
    for provider in application.findall("provider"):
        if provider.attrib.get(android_ns + "name") == "androidx.core.content.FileProvider":
            provider.set(android_ns + "name", "com.youki.dex.utils.DevDexFileProvider")
    for activity in application.findall("activity"):
        for intent_filter in list(activity.findall("intent-filter")):
            actions = {n.attrib.get(android_ns + "name") for n in intent_filter.findall("action")}
            categories = {n.attrib.get(android_ns + "name") for n in intent_filter.findall("category")}
            if "android.intent.action.MAIN" in actions and categories.intersection({
                "android.intent.category.HOME",
                "android.intent.category.SECONDARY_HOME",
            }):
                activity.remove(intent_filter)
ET.indent(tree, space="    ")
tree.write(engine_manifest, encoding="utf-8", xml_declaration=True)

provider_src = engine_src / "java/com/youki/dex/utils/DevDexFileProvider.kt"
provider_src.parent.mkdir(parents=True, exist_ok=True)
provider_src.write_text("""package com.youki.dex.utils

/** Separate component for DevDex file sharing inside the combined APK. */
class DevDexFileProvider : androidx.core.content.FileProvider()
""", encoding="utf-8")

# Patch Quickstep's app-icon launch path. The windowing mode is applied after bounds.
quickstep = root / "quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java"
java = quickstep.read_text(encoding="utf-8")
if "DEVDEX_PLUS_FREEFORM_PROTOTYPE" not in java:
    anchor = """        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
"""
    if anchor not in java:
        raise SystemExit("QuickstepLauncher launch-options patch anchor not found")

    block = """
        // DEVDEX_PLUS_FREEFORM_PROTOTYPE:
        // Apply DevDex freeform launch policy to app icons and deep shortcuts.
        // Per-app remembered launch modes are not bridged into Quickstep yet.
        ItemInfo devDexLaunchItem = item;
        if (devDexLaunchItem == null && v != null && v.getTag() instanceof ItemInfo) {
            devDexLaunchItem = (ItemInfo) v.getTag();
        }
        if (devDexLaunchItem != null
                && devDexLaunchItem.getTargetPackage() != null
                && (devDexLaunchItem.itemType == ITEM_TYPE_APPLICATION
                    || devDexLaunchItem.itemType == ITEM_TYPE_DEEP_SHORTCUT)) {
            android.content.SharedPreferences devDexPrefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            boolean enabled = devDexPrefs.getBoolean("devdex_plus_freeform_launch", true);
            boolean alwaysFloating = devDexPrefs.getBoolean("always_floating", false);
            String globalLaunchMode = devDexPrefs.getString("launch_mode", "standard");
            if (enabled && (alwaysFloating || !"fullscreen".equals(globalLaunchMode))) {
                android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
                int displayWidth = Math.max(1, metrics.widthPixels);
                int displayHeight = Math.max(1, metrics.heightPixels);
                int width = Math.max(1, displayWidth * 3 / 4);
                int height = Math.max(1, displayHeight * 3 / 4);
                int left = (displayWidth - width) / 2;
                int top = (displayHeight - height) / 2;
                activityOptions.options.setLaunchBounds(
                        new android.graphics.Rect(left, top, left + width, top + height));
                activityOptions.options.setLaunchWindowingMode(
                        android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM);
            }
        }
"""
    java = java.replace(anchor, anchor + block, 1)
    quickstep.write_text(java, encoding="utf-8")

print("Prepared Launcher3 + DevDex source integration.")
print("Freeform: enabled by default; global fullscreen setting is respected.")
print("Per-app remembered launch modes are not bridged into Quickstep in this prototype.")
PY

echo "== Static verification =="
grep -q "applicationId 'com.devboi.dex'" build.gradle
grep -q "DevDexPlusApplication" AndroidManifest-common.xml
grep -q "DEVDEX_PLUS_FREEFORM_PROTOTYPE" quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java
grep -q "include ':devdexengine'" settings.gradle
test -f devdexengine/src/main/java/com/youki/dex/services/PerfectServer.kt
test -f devdexengine/src/main/java/com/youki/dex/utils/WindowControlsManager.kt

echo "== Build Launcher3 Android 16-S2 + DevDex Window Engine =="
gradle assembleAospWithQuickstepDebug --stacktrace
