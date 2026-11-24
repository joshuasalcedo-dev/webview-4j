package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * For a release: the release key, and the signed manifest an app checks. Run with webview-4j's core on the class path
 * (the sample's build has Gradle tasks for both):
 * <pre>
 * java ... UpdateTool keygen &lt;private-key-file&gt;
 *     a new Ed25519 key pair: the private key into the file (keep it as a CI secret, never in the repository);
 *     prints the public key, for the app's update.publicKey
 *
 * java ... UpdateTool manifest --app notes --version 0.3.0 --changelog CHANGELOG.md --out build/update/manifest.json
 *         --download windows-x64=build/jpackage/Notes-0.3.0.msi=https://downloads.example.com/Notes-0.3.0.msi ...
 *     the manifest (each download's SHA-256 and size, the version's changelog section) and its signature, .sig;
 *     the private key from the WEBVIEW4J_UPDATE_KEY environment variable, or --key-file
 * </pre>
 */
public final class UpdateTool {

    private UpdateTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage();
        switch (args[0]) {
            case "keygen" -> {
                if (args.length < 2) usage();
                String[] keys = UpdateSignature.newKeys();
                Path file = Path.of(args[1]);
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Files.writeString(file, keys[1]);
                try {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException e) {
                    // Windows: the folder's own permissions
                }
                System.out.println("The private key is in " + file + ": keep it secret (a CI secret), never in the repository.");
                System.out.println("update.publicKey=" + keys[0]);
            }
            case "manifest" -> manifest(args);
            default -> usage();
        }
    }

    private static void manifest(String[] args) throws Exception {
        Map<String, String> one = new LinkedHashMap<>();
        List<String> downloads = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String name = args[i];
            if (!name.startsWith("--") || i + 1 >= args.length) usage();
            String value = args[++i];
            if (name.equals("--download")) downloads.add(value);
            else one.put(name.substring(2), value);
        }
        for (String needed : List.of("app", "version", "out")) if (!one.containsKey(needed)) usage();
        String key = System.getenv("WEBVIEW4J_UPDATE_KEY");
        if (one.containsKey("key-file")) key = Files.readString(Path.of(one.get("key-file"))).trim();
        if (key == null || key.isBlank()) throw new IllegalStateException("no release key: set WEBVIEW4J_UPDATE_KEY or give --key-file");

        Version version = Version.of(one.get("version"));
        String notes = null;
        String date = null;
        if (one.containsKey("changelog")) {
            var release = ReleaseNotes.parse(Files.readString(Path.of(one.get("changelog")))).of(version.text());
            if (release.isPresent()) {
                notes = release.get().body();
                date = release.get().date();
            }
        }
        Map<String, UpdateManifest.Download> d = new LinkedHashMap<>();
        for (String spec : downloads) {
            String[] parts = spec.split("=", 3);
            if (parts.length != 3) throw new IllegalArgumentException("--download is platform=file=url: " + spec);
            Path file = Path.of(parts[1]);
            d.put(parts[0], new UpdateManifest.Download(URI.create(parts[2]), sha256(file), Files.size(file)));
        }
        UpdateManifest manifest = new UpdateManifest(one.get("app"), version, date, notes, d);
        byte[] json = manifest.toJson();
        Path out = Path.of(one.get("out"));
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.write(out, json);
        Files.writeString(Path.of(out + ".sig"), UpdateSignature.sign(json, key));
        System.out.println("Wrote " + out + " and " + out + ".sig for " + manifest.app() + " " + version + " (" + d.keySet() + ")");
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) sha.update(buffer, 0, n);
            return HexFormat.of().formatHex(sha.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void usage() {
        System.err.println("UpdateTool keygen <private-key-file>");
        System.err.println("UpdateTool manifest --app <name> --version <v> --out <manifest.json> [--changelog CHANGELOG.md] "
                + "[--key-file <file>] --download <platform>=<file>=<url> ...");
        System.exit(2);
    }
}
