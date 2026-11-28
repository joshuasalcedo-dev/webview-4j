# swaswa-core

`swaswa-core` is the stable, batteries-included dependency for a swaswa application. It exposes the supported Java
module graph through Maven and JPMS, so app authors and generated project templates do not need to select internal
feature dependencies individually.

```xml
<dependency>
    <groupId>io.joshuasalcedo.swaswa</groupId>
    <artifactId>swaswa-core</artifactId>
    <version>${swaswa.version}</version>
</dependency>
```

The graph includes `swaswa-ffi` (native bridge), `swaswa-webview`, `swaswa-dom`, `swaswa-react`, `swaswa-tailwind`,
`swaswa-ui`, and `swaswa-shadcn`. Feature artifacts remain independently usable for applications that need a smaller
dependency set. `swaswa-core` is the application-facing aggregate.

## Module boundary

The aggregate establishes one stable entry point without changing the published packages used by existing source
code. The JavaFX-like app lifecycle and its HTML/component/state APIs are still implemented in `swaswa-ffi` today,
alongside the actual FFM bridge. A later internal extraction can move those implementations into dedicated core
packages while preserving their Java package names and keeping this dependency as the app's entry point. The one-way
dependency rule for that extraction is core → bridge; the bridge must never depend on core.

Generated Java and web resources belong to the feature that owns their inputs. Maven lifecycle executions in those
feature modules generate and package their outputs before the core aggregate is assembled. The checked-in native
libraries remain packaged by `swaswa-ffi`; rebuilding them requires the explicit `-Dnative.build` profile.
