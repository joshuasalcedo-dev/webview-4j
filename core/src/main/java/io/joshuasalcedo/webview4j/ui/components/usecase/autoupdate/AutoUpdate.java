package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiException;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.state.Codec;
import io.joshuasalcedo.webview4j.ui.state.Saved;
import io.joshuasalcedo.webview4j.ui.state.Session;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * The app keeps itself up to date and says what changed, with one call:
 * <ul>
 *   <li><b>What's new</b>: the first time it starts on a new version, a window with the changes since the version
 *       last run (from its CHANGELOG.md, put in its jar by the build);</li>
 *   <li><b>Updates</b>: now and every few hours, the release's signed manifest is checked; a newer version shows its
 *       notes with Update now, Later and Skip this version; it is downloaded, checked, and its installer run;</li>
 *   <li><b>Changelog</b>: {@link #openChangelog} shows the whole changelog, for a Help menu.</li>
 * </ul>
 * <pre>{@code
 * App.run(app -> {
 *     AutoUpdate.start(app);                 // what the build put in META-INF/webview4j/app.properties
 *     ...
 * });
 * }</pre>
 * Without an update manifest and key in the app's release, it does What's new and the changelog only.
 */
public final class AutoUpdate {

    private static final System.Logger LOG = System.getLogger(AutoUpdate.class.getName());

    /** What the app knows about its release: its name, its version, where its manifest is, the release key. */
    public record AppRelease(String name, Version version, URI manifest, String publicKey) {

        /** Where the build puts it in the app's jar. */
        public static final String RESOURCE = "META-INF/webview4j/app.properties";

        /** What the build put in the app's jar ({@code name}, {@code version}, {@code update.manifest}, {@code update.publicKey}). */
        public static AppRelease bundled() {
            Properties p = new Properties();
            ClassLoader loader = Thread.currentThread().getContextClassLoader() != null ? Thread.currentThread().getContextClassLoader()
                    : AutoUpdate.class.getClassLoader();
            try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new IllegalStateException(RESOURCE + " is not in the app: the build writes it (see the sample's build)");
                p.load(in);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            String manifest = p.getProperty("update.manifest", "").trim();
            String key = p.getProperty("update.publicKey", "").trim();
            return new AppRelease(p.getProperty("name", "App"), Version.of(p.getProperty("version", "0.0.0")),
                    manifest.isEmpty() ? null : URI.create(manifest), key.isEmpty() ? null : key);
        }

        boolean updates() {
            return manifest != null && publicKey != null;
        }
    }

    private final App app;
    private final AppRelease release;
    private final Duration every;
    private final Session session;
    private final Saved<String> lastRun;
    private final Saved<String> skipped;
    private final Saved<Long> laterUntil;
    private volatile Updater updater;
    private volatile UiWindow prompt;
    private volatile Thread checker;

    private AutoUpdate(App app, AppRelease release, Duration every) {
        this.app = app;
        this.release = release;
        this.every = every;
        this.session = UiWindow.session(app);
        this.lastRun = Saved.of("autoupdate.last-run", String.class, () -> null);
        this.skipped = Saved.of("autoupdate.skipped", String.class, () -> null);
        this.laterUntil = Saved.of("autoupdate.later-until", Codec.of(Long.class), () -> 0L);
        if (release.updates()) this.updater = Updater.of(release.manifest(), release.publicKey(), release.version());
    }

    /** With what the build put in the app's jar, checking every 6 hours. */
    public static AutoUpdate start(App app) {
        return start(app, AppRelease.bundled(), Duration.ofHours(6));
    }

    public static AutoUpdate start(App app, AppRelease release, Duration every) {
        AutoUpdate auto = new AutoUpdate(Objects.requireNonNull(app, "app"), Objects.requireNonNull(release, "release"), every);
        auto.whatsNew();
        if (release.updates()) auto.schedule();
        else LOG.log(System.Logger.Level.INFO, "no update manifest or key in the app's release: What's new and the changelog only");
        return auto;
    }

    /** For tests and settings: the updater in use (a different installer, a folder for downloads). */
    public AutoUpdate updater(Updater updater) {
        this.updater = updater;
        return this;
    }

    // ---- What's new ----

    /** The first run on a new version: the changes since the version last run. The very first run: nothing to say. */
    private void whatsNew() {
        String was = session.call(lastRun::get);
        session.call(() -> {
            lastRun.set(release.version().text());
            return null;
        });
        session.flush();
        if (was == null || !Version.is(was) || !release.version().newerThan(Version.of(was))) return;
        Ui.open(app, new WhatsNew(release, was), o -> o.title("What's new in " + release.name()).size(560, 560));
    }

    private static final class WhatsNew implements Screen {
        private final AppRelease release;
        private final String since;

        WhatsNew(AppRelease release, String since) {
            this.release = release;
            this.since = since;
        }

        @Override
        public Component<?> render() {
            return column(
                    text("What's new in " + release.name() + " " + release.version()).variant(TextVariant.TITLE),
                    text("Since " + since + ", the version you had.").tone(Tone.SECONDARY),
                    Changelog.bundled().since(since).upTo(release.version().text()).empty("Nothing written down for this version."),
                    button("OK").accent().onClick(() -> UiWindow.current().ifPresent(w -> w.view().close()))).padding(24).gap(12).scroll();
        }
    }

    /** The whole changelog, in a window of its own (for a Help menu). */
    public static CompletableFuture<UiWindow> openChangelog(App app) {
        AppRelease release = AppRelease.bundled();
        return Ui.open(app, () -> column(text(release.name() + " changelog").variant(TextVariant.TITLE),
                Changelog.bundled().upTo(release.version().text())).padding(24).gap(12).scroll(), o -> o.title(release.name() + ": changelog").size(640, 640));
    }

    // ---- updates ----

    private void schedule() {
        checker = Thread.ofVirtual().name("webview4j-autoupdate").start(() -> {
            try {
                Thread.sleep(Duration.ofSeconds(5));
                while (!Thread.currentThread().isInterrupted()) {
                    checkNow().exceptionally(e -> null);
                    Thread.sleep(every);
                }
            } catch (InterruptedException e) {
                // the app stops
            }
        });
        app.whenStopped().thenRun(checker::interrupt);
    }

    /** Checks now: a newer version (not skipped, not put off) opens the update prompt. */
    public CompletableFuture<Optional<UpdateManifest>> checkNow() {
        Updater u = updater;
        if (u == null) return CompletableFuture.completedFuture(Optional.empty());
        return u.check().thenApply(found -> {
            found.ifPresent(m -> {
                String skip = session.call(skipped::get);
                long later = session.call(laterUntil::get);
                if (m.version().text().equals(skip) || System.currentTimeMillis() < later || prompt != null) return;
                prompt = Ui.open(app, new Prompt(m), o -> o.title("Update " + release.name()).size(560, 520)).join();
            });
            return found;
        }).whenComplete((r, e) -> {
            if (e != null) LOG.log(System.Logger.Level.INFO, "the update check failed: " + e.getMessage());
        });
    }

    /** The prompt: the new version's notes, then the download, then the installer. */
    private final class Prompt implements Screen {
        private final UpdateManifest release;
        private UpdatePrompt.Status status = UpdatePrompt.Status.AVAILABLE;
        private double progress;
        private String error;
        private Path downloaded;

        Prompt(UpdateManifest release) {
            this.release = release;
        }

        @Override
        public Component<?> render() {
            return new UpdatePrompt(AutoUpdate.this.release.name(), release.version().text())
                    .current(AutoUpdate.this.release.version().text())
                    .notes(release.notes() != null ? release.notes() : ReleaseNotes.bundled().of(release.version().text()).map(ReleaseNotes.Release::body).orElse(null))
                    .status(status).progress(progress).error(error)
                    .onUpdate(this::download)
                    .onInstall(this::install)
                    .onLater(() -> close(() -> laterUntil.set(System.currentTimeMillis() + Duration.ofDays(1).toMillis())))
                    .onSkip(() -> close(() -> skipped.set(release.version().text())));
        }

        private void download() {
            UiWindow window = prompt;
            status = UpdatePrompt.Status.DOWNLOADING;
            error = null;
            updater.download(release, p -> {
                progress = p;
                if (window != null) window.refresh();
            }).whenComplete((path, e) -> {
                if (e != null) {
                    status = UpdatePrompt.Status.FAILED;
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    error = cause instanceof UiException ui ? ui.userMessage() : "The update could not be downloaded.";
                } else {
                    downloaded = path;
                    status = UpdatePrompt.Status.READY;
                }
                if (window != null) window.refresh();
            });
        }

        private void install() {
            status = UpdatePrompt.Status.INSTALLING;
            updater.install(downloaded);
            session.flush();
            app.quit();
        }

        private void close(Runnable remember) {
            remember.run();
            session.flush();
            UiWindow window = prompt;
            prompt = null;
            if (window != null) window.view().close();
        }
    }
}
