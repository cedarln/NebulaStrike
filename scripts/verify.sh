#!/usr/bin/env bash
# Sandbox verification: type-check every source against the official android.jar,
# then run the pure-Java core unit tests with JUnit. No Android SDK/Gradle needed.
set -euo pipefail
cd "$(dirname "$0")/.."

SDK_JAR="${SDK_JAR:-/opt/android-sdk/platforms/android-34/android.jar}"
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
