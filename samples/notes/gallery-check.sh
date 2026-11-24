#!/usr/bin/env bash
# Checks the gallery's pages of one category: they compile, and each one draws in a real (hidden) window.
#   ./gallery-check.sh Layout        one category (its section name in the side bar)
#   ./gallery-check.sh               every page, and that every component has one
# Builds take turns (a lock folder), so several people (or agents) can check at once.
set -u
cd "$(dirname "$0")"
lock=build/.gallery-check.lock
mkdir -p build
until mkdir "$lock" 2>/dev/null; do sleep 2; done
trap 'rmdir "$lock"' EXIT

if [ $# -gt 0 ]; then
  tests=(--tests '*GalleryTest.everyPageDraws*' --tests '*GalleryTest.aPageCoversOnlyComponents' "-Pgallery.section=$1")
else
  tests=(--tests '*GalleryTest')
fi
CARGO_BUILD_JOBS=2 ./gradlew test "${tests[@]}" -PcargoProfile=dev --console=plain -q 2>&1 | grep -E "error:|warning:|\.java:[0-9]+" -A2 | head -40
status=${PIPESTATUS[0]}
for f in build/test-results/test/TEST-*GalleryTest.xml; do
  [ -f "$f" ] || continue
  grep -hoE 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$f"
  grep -hoE '<failure message="[^"]{0,4000}' "$f" | sed 's/&quot;/"/g; s/&lt;/</g; s/&gt;/>/g; s/&#10;/\n/g'
done
[ "$status" = 0 ] && echo "PASSED" || echo "FAILED"
exit "$status"
