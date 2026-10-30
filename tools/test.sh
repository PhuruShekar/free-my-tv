#!/usr/bin/env bash
# Checks the app's logic that has no Android in it (app/src/io/freemytv/Rules.java) on this computer, with only a JDK:
#
#   tools/test.sh
#
# The tests are app/test/io/freemytv/*Test.java, plain Java with a main (no Gradle, no JUnit). Looks and speed aren't
# here: those are checked on the TV.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "JDK not found. Install JDK 17 and set JAVA_HOME."; exit 1; }
rm -rf out/test
mkdir -p out/test
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -d out/test app/src/io/freemytv/Rules.java app/test/io/freemytv/*Test.java
for t in app/test/io/freemytv/*Test.java; do
  name="$(basename "$t" .java)"
  echo "$name:"
  "$JAVA" -cp out/test "io.freemytv.$name"
done
