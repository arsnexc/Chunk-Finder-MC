#!/usr/bin/env bash
# Dependency-free UI checks without resolving Minecraft, Fabric, or Gradle dependencies.
set -euo pipefail
cd "$(dirname "$0")/.."

output=build/ui-regression
mkdir -p "$output/classes"
find src/client/java/dev/chunkfinder/ui src/uiTest/java/dev/chunkfinder/ui -name '*.java' -print | sort > "$output/sources.txt"
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
javac_bin="${JAVA_HOME:+$JAVA_HOME/bin/}javac"

if [[ -n "${UI_TEST_COMPILER_JAR:-}" ]]; then
    # Optional Eclipse JDT batch compiler for environments with only a Java runtime.
    "$java_bin" -jar "$UI_TEST_COMPILER_JAR" -source 21 -target 21 -proc:none -d "$output/classes" @"$output/sources.txt"
else
    "$javac_bin" --release 21 -d "$output/classes" @"$output/sources.txt"
fi
"$java_bin" -Djava.awt.headless=true -cp "$output/classes" dev.chunkfinder.ui.UiRegressionTest
if [[ "${1:-}" == "--preview" ]]; then
    "$java_bin" -Djava.awt.headless=true -cp "$output/classes" dev.chunkfinder.ui.UiPreview "${2:-build/ui-preview.png}"
fi
