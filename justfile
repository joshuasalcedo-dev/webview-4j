# swaswa: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# Every swaswa-ffi feature in one window: windows, title bars, taskbar, the page, dialogs, the system, and its events
ffi-showcase:
    mvn -q -B -pl swaswa-ffi test-compile exec:exec@showcase

# swaswa-webview: an app's own page (plain HTML here) whose requests a Java @WebViewController answers: notes
webview-showcase:
    mvn -q -B -pl swaswa-ffi install -DskipTests
    mvn -q -B -pl swaswa-webview test-compile exec:exec@showcase

# swaswa-tailwind: a page styled the Tailwind way (typed classes, a CSS file of its own), showing its own code
tailwind-demo:
    mvn -q -B -pl swaswa-ffi,swaswa-tailwind,swaswa-dom install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@tailwind-demo

# every Tailwind class, as Tailwind's docs show them: its Java name, its CSS, a preview
tailwind-docs:
    mvn -q -B -pl swaswa-ffi,swaswa-tailwind,swaswa-dom install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@tailwind-docs

# The docs as web pages, no window: one HTML file per family in swaswa-demo/target/docs-pages
tailwind-pages:
    mvn -q -B -pl swaswa-ffi,swaswa-tailwind,swaswa-dom install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@tailwind-pages

# swaswa-dom: the page from Java, typed: live values, scripts, the page's functions, the clipboard
dom-showcase:
    mvn -q -B -pl swaswa-ffi install -DskipTests
    mvn -q -B -pl swaswa-dom test-compile exec:exec@showcase

# Build and test everything (the native library from swaswa-ffi/native/lib, no Rust needed)
verify:
    mvn -B verify

# Rebuild the native library with cargo (release) into swaswa-ffi/native/lib/<os>-<arch>/: commit it after
native:
    mvn -B -pl swaswa-ffi -Dnative.build -DskipTests process-resources

# The native library unoptimised, built faster, for trying a Rust change (into native/lib too: rebuild with `just native` before committing)
native-dev:
    mvn -B -pl swaswa-ffi -Dnative.build "-Dcargo.profile=dev" -DskipTests process-resources
