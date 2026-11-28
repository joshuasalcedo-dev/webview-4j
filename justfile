# swaswa: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# Build the stable, batteries-included application module and every feature it assembles
core-install:
    mvn -q -B -pl swaswa-core -am install -DskipTests

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

# shadcn/ui's real components from Java, Swing-style (ShadcnDemo)
shadcn-demo:
    mvn -q -B -pl swaswa-ffi,swaswa-tailwind,swaswa-react,swaswa-shadcn install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@shadcn-demo

# shadcn/ui's components, their CSS and their Java classes again (after `bunx --bun shadcn@latest add <name>` in swaswa-shadcn/src/build/ui)
shadcn-build:
    cd swaswa-shadcn/src/build && bun build.ts

# every shadcn/ui component (or some: just shadcn-add-all calendar sonner), then the bundle and their Java packages
shadcn-add-all *names:
    cd swaswa-shadcn/src/build && bun add-all.ts {{names}}

# swaswa-ui's desktop controls (the system's look) from Java, Swing-style (UiDemo)
ui-demo:
    mvn -q -B -pl swaswa-ffi,swaswa-react,swaswa-ui install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@ui-demo

# swaswa-ui's chat from Java: a ChatService in memory, Ana answers (ChatDemo)
chat-demo:
    mvn -q -B -pl swaswa-ffi,swaswa-react,swaswa-ui install -DskipTests
    mvn -q -B -pl swaswa-demo compile exec:exec@chat-demo

# swaswa-ui's served files and Java classes again (after changing swaswa-ui/src/build/ui)
ui-build:
    cd swaswa-ui/src/build/ui && bun install
    cd swaswa-ui/src/build && bun build.ts

# React in the jar: rebuild swaswa-react's runtime (React, ReactDOM, the bridge) after changing src/build
react-runtime:
    cd swaswa-react/src/build && bun install && bun run build

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

# the swaswa command, installed in ~/.swaswa (put ~/.swaswa/bin on the PATH): swaswa new | run | build
cli-install:
    mvn -q -B -pl swaswa-cli package
    rm -rf ~/.swaswa
    unzip -q -o swaswa-cli/target/swaswa-cli-*.zip -d ~/.swaswa-tmp && mv ~/.swaswa-tmp/swaswa ~/.swaswa && rm -rf ~/.swaswa-tmp
    @echo "installed: ~/.swaswa/bin/swaswa"
