#!/usr/bin/env bash
# Sandbox verification: type-check every source against the official android.jar,
# then run the pure-Java core unit tests with JUnit. No Android SDK/Gradle needed.
set -euo pipefail
cd "$(dirname "$0")/.."

# 类型检查只需 android.jar；优先 compileSdk 34，其次本机任意已安装平台
if [ -z "${SDK_JAR:-}" ]; then
    for root in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" /opt/android-sdk "$HOME/Library/Android/sdk"; do
        [ -n "$root" ] || continue
        for jar in "$root"/platforms/android-34/android.jar "$root"/platforms/android-*/android.jar; do
            [ -f "$jar" ] && { SDK_JAR="$jar"; break 2; }
        done
    done
fi
[ -n "${SDK_JAR:-}" ] && [ -f "$SDK_JAR" ] \
    || { echo "未找到 android.jar，请设置 SDK_JAR 指向它" >&2; exit 1; }
echo "android.jar: $SDK_JAR"
LIB=".verify-libs"
mkdir -p "$LIB" "build/verify"

fetch() { # fetch <url> <file>
  [ -f "$2" ] || curl -sSfL --retry 2 -o "$2" "$1"
}
fetch https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar "$LIB/junit.jar"
fetch https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar "$LIB/hamcrest.jar"

echo "== 1/4 type-check main sources against official android.jar =="
find app/src/main/java -name '*.java' > build/verify/main-sources.txt
javac -Xlint:all,-serial -cp "$SDK_JAR" -d build/verify/main @build/verify/main-sources.txt
echo "main OK"

echo "== 2/4 compile core tests =="
find app/src/test/java -name '*.java' > build/verify/test-sources.txt
javac -cp "$SDK_JAR:build/verify/main:$LIB/junit.jar:$LIB/hamcrest.jar" \
  -d build/verify/test @build/verify/test-sources.txt
echo "tests compile OK"

echo "== 3/4 run unit tests =="
TEST_CLASSES=$(find app/src/test/java -name '*Test.java' | sed 's#app/src/test/java/##; s#/#.#g; s#\.java$##' | sort | tr '\n' ' ')
java -cp "$SDK_JAR:build/verify/main:build/verify/test:$LIB/junit.jar:$LIB/hamcrest.jar" \
  org.junit.runner.JUnitCore $TEST_CLASSES

echo "== 4/4 sim micro-benchmark =="
java -cp "$SDK_JAR:build/verify/main:build/verify/test" com.nebulastrike.game.core.SimBench
