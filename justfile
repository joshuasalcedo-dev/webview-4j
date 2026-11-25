# webview-4j: shortcuts. `just` lists them.
set windows-shell := ["powershell.exe", "-NoLogo", "-NoProfile", "-Command"]

default:
    @just --list

# How fast each path is, in a real hidden window (Windows only): rebuilds, runs Benchmark, prints its numbers
bench:
    ./gradlew :core:test --tests '*Benchmark' -Pbenchmark --rerun --console=plain
    Select-String -Path core/build/test-results/test/TEST-*Benchmark.xml -Pattern 'BENCH ' | ForEach-Object { $_.Line -replace '^.*?BENCH ', '' }

# The Sampler: every component, working, with the Java for each, in one app (samples/sampler)
sampler:
    cd samples/sampler; ./gradlew run -PcargoProfile=dev --console=plain

# The Sampler on the web: the same Java screens in a browser, http://localhost:8080/
sampler-web port="8080":
    cd samples/sampler; ./gradlew runWeb -Pport={{port}} -PcargoProfile=dev --console=plain

# Checks English text with Harper (crates/grammar): just grammar "This is an test."  (dialect: en-US, en-GB, en-AU, en-CA, en-IN)
grammar text dialect="en-US":
    cargo run -q --release -p grammar --example check -- --dialect {{dialect}} "{{text}}"

# Records what you do in the Sampler until you close it, saved as an automation (a JSON script)
sampler-record file="docs/tour.automation.json":
    cd samples/sampler; ./gradlew run --args="--record {{absolute_path(file)}}" -PcargoProfile=dev --console=plain

# Replays an automation in the Sampler: its screenshots and recordings next to the file; fails if a step does
automate file="docs/tour.automation.json":
    cd samples/sampler; ./gradlew run --args="--automate {{absolute_path(file)}}" -PcargoProfile=dev --console=plain

# The Sampler as a native executable (GraalVM native-image): samples/sampler/build/native/
sampler-native:
    cd samples/sampler; ./gradlew nativeCompile --console=plain

# The Notes template (a React page with Java behind it) and its showcase window
showcase:
    cd samples/notes; ./gradlew run -Pshowcase -PcargoProfile=dev --console=plain

# The Notes template on its own: how an app with a React page is made
notes:
    cd samples/notes; ./gradlew run -PcargoProfile=dev --console=plain

# The React kit's stories (Storybook on http://localhost:6006)
storybook:
    cd core/src/main/ui; bun install; bun run storybook

# Checks the Sampler: every component has a page, every page draws (one section: just sampler-check Panes)
sampler-check section="":
    bash samples/sampler/sampler-check.sh {{section}}
