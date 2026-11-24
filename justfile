# webview-4j: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# How fast each path is, in a real hidden window (Windows only): rebuilds, runs Benchmark, prints its numbers
bench:
    ./gradlew :core:test --tests '*Benchmark' -Pbenchmark --rerun --console=plain
    Select-String -Path core/build/test-results/test/TEST-*Benchmark.xml -Pattern 'BENCH ' | ForEach-Object { $_.Line -replace '^.*?BENCH ', '' }

# The component gallery: every component, written only in Java, with the Java for each (samples/notes)
gallery:
    cd samples/notes; ./gradlew run -Pgallery -PcargoProfile=dev --console=plain

# The component gallery on the web: the same Java screens in a browser, http://localhost:8080/
gallery-web port="8080":
    cd samples/notes; ./gradlew runWeb -Pport={{port}} -PcargoProfile=dev --console=plain

# The sample app (Notes) and its showcase window: sidebar, MDI, an embedded site
showcase:
    cd samples/notes; ./gradlew run -Pshowcase -PcargoProfile=dev --console=plain

# The sample app (Notes) on its own
notes:
    cd samples/notes; ./gradlew run -PcargoProfile=dev --console=plain

# The React kit's stories (Storybook on http://localhost:6006)
storybook:
    cd core/src/main/ui; bun install; bun run storybook

# Checks the gallery: every component has a page, every page draws (one category: just gallery-check Layout)
gallery-check section="":
    bash samples/notes/gallery-check.sh {{section}}
