#!/usr/bin/env bash
# Checks the app's logic that has no Android in it (app/src/io/freemytv/Rules.java, Transit.java, and Sports.java) on this computer, with only a JDK:
#
#   tools/test.sh
#
# The tests are app/test/io/freemytv/*Test.java, plain Java with a main (no Gradle, no JUnit). Looks and speed aren't
# here: those are checked on the TV. SportsFeedTest reads feeds saved from ESPN (app/test/feeds) with org.json, which
# Android has but a JDK doesn't: put org.json's jar (Maven Central, org/json/json) in tools/lib, or it's skipped.
set -euo pipefail
cd "$(dirname "$0")/.."
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVAC" >/dev/null 2>&1 || { echo "JDK not found. Install JDK 17 and set JAVA_HOME."; exit 1; }
rm -rf out/test
mkdir -p out/test
SEP=:
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac   # Windows Java wants ; between classpath entries
JSON="$(ls tools/lib/json-*.jar 2>/dev/null | head -1 || true)"
SRC="app/src/io/freemytv/Rules.java app/src/io/freemytv/Transit.java app/src/io/freemytv/Sports.java"
TESTS="$(ls app/test/io/freemytv/*Test.java | grep -v SportsFeedTest || true)"
if [ -n "$JSON" ]; then
  SRC="$SRC app/src/io/freemytv/SportsFeed.java"
  TESTS="$TESTS app/test/io/freemytv/SportsFeedTest.java"
else
  echo "SportsFeedTest skipped: no tools/lib/json-*.jar"
fi
# shellcheck disable=SC2086
"$JAVAC" -nowarn -Xlint:-options -source 8 -target 8 -cp "${JSON:-.}" -d out/test $SRC $TESTS
for t in $TESTS; do
  name="$(basename "$t" .java)"
  echo "$name:"
  "$JAVA" -cp "out/test${JSON:+$SEP$JSON}" "io.freemytv.$name"
done
