package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import io.joshuasalcedo.webview4j.ui.UiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;

/**
 * Finds, fetches and installs the app's next version, safely: the manifest is believed only when its signature is
 * the release key's, only a newer version is taken, the installer comes over https and must have the manifest's size
 * and SHA-256 before it runs. The installer is the system's own (MSI, DMG or PKG, DEB or RPM), as jpackage makes them.
 * <pre>{@code
 * Updater updater = Updater.of(URI.create("https://api.example.com/notes/manifest.json"), PUBLIC_KEY, Version.of("0.2.0"));
 * updater.check().thenAccept(found -> found.ifPresent(release -> ...));
 * }</pre>
 * {@link AutoUpdate} does all of it with its windows; this is the part without them.
 */
public final class Updater {

    /** Runs a downloaded installer (the system's way unless set: msiexec, open, xdg-open). */
    @FunctionalInterface
    public interface Installer {
        void run(Path installer) throws IOException;
    }

    private final URI manifest;
    private final String publicKey;
    private final Version current;
    private Installer installer = Updater::system;
    private Path directory;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();

    private Updater(URI manifest, String publicKey, Version current) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.current = Objects.requireNonNull(current, "current");
        this.directory = Path.of(System.getProperty("java.io.tmpdir"), "webview4j-updates");
    }

    public static Updater of(URI manifest, String publicKey, Version current) {
        return new Updater(manifest, publicKey, current);
    }

    public Updater installer(Installer installer) {
        this.installer = Objects.requireNonNull(installer, "installer");
        return this;
    }

    /** Where downloads go (the system's temporary folder unless set). */
    public Updater directory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
        return this;
    }

    public Version current() {
        return current;
    }

    /** The next version, if there is one for this machine: its manifest, signature checked. */
    public CompletableFuture<Optional<UpdateManifest>> check() {
        return CompletableFuture.supplyAsync(() -> {
            byte[] body = get(manifest);
            String signature = new String(get(URI.create(manifest + ".sig")), StandardCharsets.US_ASCII);
            if (!UpdateSignature.verify(body, signature, publicKey)) {
                throw new UiException("The update could not be trusted", "The release's signature is not the one this app expects, "
                        + "so it was not taken. Nothing changed.", UiException.Level.WARNING);
            }
            UpdateManifest m = UpdateManifest.parse(body);
            if (!m.version().newerThan(current) || m.forThisMachine().isEmpty()) return Optional.empty();
            return Optional.of(m);
        }, r -> Thread.ofVirtual().start(r));
    }

    /** Downloads the release's installer for this machine, telling how far (0 to 1); checked before it is handed back. */
    public CompletableFuture<Path> download(UpdateManifest release, DoubleConsumer progress) {
        UpdateManifest.Download d = release.forThisMachine().orElseThrow(() -> new IllegalArgumentException("no download for " + UpdateManifest.platform()));
        return CompletableFuture.supplyAsync(() -> {
            allowed(d.url());
            try {
                Files.createDirectories(directory);
                String name = Path.of(d.url().getPath()).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
                Path part = directory.resolve(name + ".part");
                Path done = directory.resolve(name);
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                HttpResponse<InputStream> res = http.send(HttpRequest.newBuilder(d.url()).timeout(Duration.ofMinutes(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (res.statusCode() != 200) throw new IOException("the download answered " + res.statusCode());
                long total = d.size() > 0 ? d.size() : res.headers().firstValueAsLong("Content-Length").orElse(-1);
                long read = 0;
                try (InputStream in = res.body(); OutputStream out = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int n; (n = in.read(buffer)) > 0; ) {
                        read += n;
                        if (d.size() > 0 && read > d.size()) {
                            out.close();
                            Files.deleteIfExists(part);
                            throw untrusted();
                        }
                        out.write(buffer, 0, n);
                        sha.update(buffer, 0, n);
                        if (total > 0) progress.accept(Math.min(1.0, (double) read / total));
                    }
                }
                String got = HexFormat.of().formatHex(sha.digest());
                if ((d.size() > 0 && read != d.size()) || !got.equalsIgnoreCase(d.sha256())) {
                    Files.deleteIfExists(part);
                    throw untrusted();
                }
                Files.move(part, done, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                progress.accept(1.0);
                return done;
            } catch (UiException e) {
                throw e;
            } catch (Exception e) {
                throw new UiException("The update could not be downloaded", "Check the connection, then try again.", UiException.Level.WARNING, e);
            }
        }, r -> Thread.ofVirtual().start(r));
    }

    private static UiException untrusted() {
        return new UiException("The update could not be trusted", "What was downloaded is not what the release says it is, "
                + "so it was thrown away. Try again later.", UiException.Level.WARNING);
    }

    /** Runs the downloaded installer; the app should quit right after, so it can be replaced. */
    public void install(Path file) {
        try {
            installer.run(file);
        } catch (IOException e) {
            throw new UiException("The installer could not start", "Open " + file.getFileName() + " yourself to finish the update.",
                    UiException.Level.WARNING, e);
        }
    }

    private byte[] get(URI uri) {
        allowed(uri);
        try {
            HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) throw new IOException(uri + " answered " + res.statusCode());
            if (res.body().length > 1024 * 1024) throw new IOException(uri + " is too big for a manifest");
            return res.body();
        } catch (IOException e) {
            throw new UiException("No update information", "The update server could not be reached. It is checked again later.", UiException.Level.INFO, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UiException("No update information", "Checking for updates was stopped.", UiException.Level.INFO, e);
        }
    }

    /** https only (plain http only to this machine, for tests and local servers). */
    private static void allowed(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.equals("https")) return;
        try {
            if (scheme.equals("http") && InetAddress.getByName(uri.getHost()).isLoopbackAddress()) return;
        } catch (IOException e) {
            // not loopback
        }
        throw new UiException("The update could not be trusted", "Updates come only over https.", UiException.Level.WARNING);
    }

    /** The system's way to run an installer: the argument alone, never through a shell. */
    static void system(Path file) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) command = name.endsWith(".msi") ? List.of("msiexec", "/i", file.toString()) : List.of(file.toString());
        else if (os.contains("mac")) command = List.of("open", file.toString());
        else command = List.of("xdg-open", file.toString());
        new ProcessBuilder(command).inheritIO().start();
    }
}
