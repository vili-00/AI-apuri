#!/usr/bin/env bash
set -u

# AI-apuri environment check script
# Safe to run. This script only reads environment/tooling information.
# It does not install, delete, or modify project files.

echo "AI-apuri environment check"
echo "=========================="
echo

FAIL=0

check_path() {
  local label="$1"
  local path="$2"

  if [ -e "$path" ]; then
    echo "✅ $label exists: $path"
  else
    echo "❌ $label missing: $path"
    FAIL=1
  fi
}

check_executable() {
  local label="$1"
  local path="$2"

  if [ -x "$path" ]; then
    echo "✅ $label executable: $path"
  else
    echo "❌ $label not executable or missing: $path"
    FAIL=1
  fi
}

echo "Expected tool locations"
echo "-----------------------"

JDK_DIR="${JAVA_HOME:-/home/agent/jdk}"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/home/agent/android-sdk}}"

echo "JAVA_HOME: ${JAVA_HOME:-not set, using /home/agent/jdk}"
echo "ANDROID_HOME: ${ANDROID_HOME:-not set}"
echo "ANDROID_SDK_ROOT: ${ANDROID_SDK_ROOT:-not set, using ${SDK_DIR}}"
echo

check_path "JDK directory" "$JDK_DIR"
check_executable "java" "$JDK_DIR/bin/java"
check_executable "javac" "$JDK_DIR/bin/javac"

echo
echo "Java version"
echo "------------"
if [ -x "$JDK_DIR/bin/java" ]; then
  "$JDK_DIR/bin/java" -version
else
  echo "Skipped: java not found."
fi

echo
check_path "Android SDK directory" "$SDK_DIR"
check_path "Android SDK platform-tools" "$SDK_DIR/platform-tools"
check_executable "adb" "$SDK_DIR/platform-tools/adb"

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
check_executable "sdkmanager" "$SDKMANAGER"

echo
echo "Android SDK installed components"
echo "--------------------------------"

if compgen -G "$SDK_DIR/platforms/android-*" > /dev/null; then
  echo "✅ Android platforms found:"
  ls -1 "$SDK_DIR/platforms" | sed 's/^/  - /'
else
  echo "❌ No Android SDK platforms found under $SDK_DIR/platforms"
  echo "   Expected something like: $SDK_DIR/platforms/android-35"
  FAIL=1
fi

if compgen -G "$SDK_DIR/build-tools/*" > /dev/null; then
  echo "✅ Android build-tools found:"
  ls -1 "$SDK_DIR/build-tools" | sed 's/^/  - /'
else
  echo "❌ No Android build-tools found under $SDK_DIR/build-tools"
  FAIL=1
fi

echo
echo "Project files"
echo "-------------"

if [ -f "./settings.gradle" ] || [ -f "./settings.gradle.kts" ]; then
  echo "✅ Gradle settings file found"
else
  echo "⚠️  No settings.gradle or settings.gradle.kts found in current directory"
  echo "   Run this script from the project root."
fi

if [ -f "./build.gradle" ] || [ -f "./build.gradle.kts" ]; then
  echo "✅ Root Gradle build file found"
else
  echo "⚠️  No root build.gradle or build.gradle.kts found in current directory"
fi

if [ -f "./gradlew" ]; then
  echo "✅ Gradle wrapper found: ./gradlew"
  if [ -x "./gradlew" ]; then
    echo "✅ Gradle wrapper is executable"
  else
    echo "⚠️  Gradle wrapper is not executable"
    echo "   Fix with: chmod +x ./gradlew"
  fi
else
  echo "❌ Gradle wrapper missing: ./gradlew"
  echo "   The project should use Gradle wrapper instead of relying on system Gradle."
  FAIL=1
fi

if [ -f "./gradle/wrapper/gradle-wrapper.properties" ]; then
  echo "✅ Gradle wrapper properties found"
else
  echo "❌ Missing gradle/wrapper/gradle-wrapper.properties"
  FAIL=1
fi

echo
echo "Environment export suggestion"
echo "-----------------------------"
cat <<EOF
export JAVA_HOME=$JDK_DIR
export ANDROID_HOME=$SDK_DIR
export ANDROID_SDK_ROOT=$SDK_DIR
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/cmdline-tools/latest/bin:\$PATH"
EOF

echo
echo "Optional build check"
echo "--------------------"
if [ -x "./gradlew" ]; then
  echo "To test the project build, run:"
  echo "  ./gradlew assembleDebug"
else
  echo "After making gradlew executable, run:"
  echo "  chmod +x ./gradlew"
  echo "  ./gradlew assembleDebug"
fi

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Environment check passed."
  exit 0
else
  echo "❌ Environment check found issues."
  exit 1
fi
