#!/usr/bin/env bash
# Fast module logic tests without downloading Minecraft. Requires JDK 21+ and a Gson JAR.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${MODULE_TEST_GSON_JAR:?Set MODULE_TEST_GSON_JAR to Gson 2.13.2, or use ./gradlew moduleRegressionTest}"
output=build/module-regression
mkdir -p "$output/classes"
find src/client/java/dev/chunkfinder/core src/client/java/dev/chunkfinder/settings src/moduleTest/java -name '*.java' -print | sort > "$output/sources.txt"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
if [[ -n "${UI_TEST_COMPILER_JAR:-}" ]]; then
    "$java_bin" -jar "$UI_TEST_COMPILER_JAR" -source 21 -target 21 -proc:none -cp "$MODULE_TEST_GSON_JAR" -d "$output/classes" @"$output/sources.txt"
else
    "$javac_bin" --release 21 -cp "$MODULE_TEST_GSON_JAR" -d "$output/classes" @"$output/sources.txt"
fi
"$java_bin" -cp "$output/classes:$MODULE_TEST_GSON_JAR" dev.chunkfinder.ModuleRegressionTest
