# swaswa: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# Every swaswa-ffi feature in one window: windows, title bars, taskbar, the page, dialogs, the system, and its events
ffi-showcase:
    mvn -q -B -pl swaswa-ffi test-compile exec:exec@showcase

# Build and test everything (the native library from swaswa-ffi/native/lib, no Rust needed)
verify:
    mvn -B verify

# Rebuild the native library with cargo (release) into swaswa-ffi/native/lib/<os>-<arch>/: commit it after
native:
    mvn -B -pl swaswa-ffi -Dnative.build -DskipTests process-resources

# The native library unoptimised, built faster, for trying a Rust change (into native/lib too: rebuild with `just native` before committing)
native-dev:
    mvn -B -pl swaswa-ffi -Dnative.build "-Dcargo.profile=dev" -DskipTests process-resources
