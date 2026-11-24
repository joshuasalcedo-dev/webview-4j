package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import io.joshuasalcedo.webview4j.Json;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What a release says about itself, at a URL the app knows: the app, the version, its date and notes, and a download
 * per system, each with its SHA-256 and size. Signed (Ed25519): the signature is at the same URL with {@code .sig}
 * after it, and the app checks it against the public key built into it before it believes a word.
 * <pre>{@code
 * {"app": "notes", "version": "0.3.0", "date": "2026-12-01", "notes": "- Auto-update ...",
 *  "downloads": {"windows-x64": {"url": "https://.../Notes-0.3.0.msi", "sha256": "…", "size": 48211234}, ...}}
 * }</pre>
 */
public record UpdateManifest(String app, Version version, String date, String notes, Map<String, Download> downloads) {

    /** One system's installer. */
    public record Download(URI url, String sha256, long size) {}

    public UpdateManifest {
        downloads = Map.copyOf(downloads);
    }

    /** The installer for this machine: {@link #platform()}'s, if the release has one. */
    public Optional<Download> forThisMachine() {
        return Optional.ofNullable(downloads.get(platform()));
    }

    /** This machine's key in {@code downloads}: {@code windows-x64}, {@code macos-aarch64}, {@code linux-x64}. */
    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String o = os.contains("win") ? "windows" : os.contains("mac") ? "macos" : "linux";
        String a = arch.contains("aarch64") || arch.contains("arm64") ? "aarch64" : arch.contains("64") ? "x64" : arch;
        return o + "-" + a;
    }

    public byte[] toJson() {
        Map<String, Object> d = new LinkedHashMap<>();
        downloads.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> d.put(e.getKey(),
                Map.of("url", e.getValue().url().toString(), "sha256", e.getValue().sha256(), "size", e.getValue().size())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", app);
        m.put("version", version.text());
        if (date != null) m.put("date", date);
        if (notes != null) m.put("notes", notes);
        m.put("downloads", d);
        return Json.write(m).getBytes(StandardCharsets.UTF_8);
    }

    public static UpdateManifest parse(byte[] json) {
        Map<String, Object> m = Json.object(new String(json, StandardCharsets.UTF_8));
        Map<String, Download> downloads = new LinkedHashMap<>();
        if (m.get("downloads") instanceof Map<?, ?> d) {
            d.forEach((k, v) -> {
                if (v instanceof Map<?, ?> x) {
                    downloads.put(String.valueOf(k), new Download(URI.create(String.valueOf(x.get("url"))), String.valueOf(x.get("sha256")),
                            x.get("size") instanceof Number n ? n.longValue() : -1));
                }
            });
        }
        return new UpdateManifest(String.valueOf(m.get("app")), Version.of(String.valueOf(m.get("version"))),
                m.get("date") == null ? null : String.valueOf(m.get("date")), m.get("notes") == null ? null : String.valueOf(m.get("notes")), downloads);
    }
}
