# webview-4j: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# How fast each path is, in a real hidden window (Windows only): rebuilds, runs Benchmark, prints its numbers
bench:
    ./gradlew :core:test --tests '*Benchmark' -Pbenchmark --rerun --console=plain
    Select-String -Path core/build/test-results/test/TEST-*Benchmark.xml -Pattern 'BENCH ' | ForEach-Object { $_.Line -replace '^.*?BENCH ', '' }

# Checks English text with Harper (crates/grammar): just grammar "This is an test."  (dialect: en-US, en-GB, en-AU, en-CA, en-IN)
grammar text dialect="en-US":
    cargo run -q --release -p grammar --example check -- --dialect {{dialect}} "{{text}}"

# The component explorer (shadcn-style gallery, live examples): just explorer   (just explorer fast: skip making the docs again)
explorer mode="":
    ./gradlew :explorer:run {{ if mode == "fast" { "'-Pexplorer.skipDocs'" } else { "" } }} --console=plain

# The React kit's stories (Storybook on http://localhost:6006)
storybook:
    cd core/src/main/ui; bun install; bun run storybook
