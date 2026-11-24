# webview-4j: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# How fast each path is, in a real hidden window (Windows only): rebuilds, runs Benchmark, prints its numbers
bench:
    ./gradlew :core:test --tests '*Benchmark' -Pbenchmark --rerun --console=plain
    Select-String -Path core/build/test-results/test/TEST-*Benchmark.xml -Pattern 'BENCH ' | ForEach-Object { $_.Line -replace '^.*?BENCH ', '' }
