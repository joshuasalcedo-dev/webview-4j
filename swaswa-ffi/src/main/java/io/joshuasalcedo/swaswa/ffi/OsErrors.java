package io.joshuasalcedo.swaswa.ffi;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** What Java's own file work met, said as the native side says it: PermissionException or SystemException. */
final class OsErrors {

    private OsErrors() {}

    /** The places only an administrator (root) may write: the system's own folders. */
    static boolean protectedPlace(Path path) {
        String p = path.toAbsolutePath().toString().toLowerCase(Locale.ROOT).replace('/', '\\');
        List<String> roots = new java.util.ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            for (String v : new String[] {"SystemRoot", "ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"}) {
                String root = System.getenv(v);
                if (root != null) roots.add(root.toLowerCase(Locale.ROOT));
            }
        } else {
            roots.addAll(List.of("\\system", "\\library", "\\applications", "\\usr", "\\etc", "\\opt", "\\bin", "\\sbin", "\\var"));
        }
        return roots.stream().anyMatch(p::startsWith);
    }

    /** The first part of {@code path} that is a file, not a folder (a path that cannot be made); null if none. */
    static Path fileInTheWay(Path path) {
        Path absolute = path.toAbsolutePath();
        for (Path at = absolute.getParent(); at != null; at = at.getParent()) {
            if (java.nio.file.Files.isRegularFile(at)) return at;
        }
        return null;
    }

    /** {@code error}, met doing {@code what} to {@code path}. */
    static WebViewException of(String what, IOException error, Path path) {
        String place = path.toAbsolutePath().toString();
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof AccessDeniedException || detail.toLowerCase(Locale.ROOT).contains("access is denied")) {
            boolean elevation = !App.elevated() && protectedPlace(path);
            String hint = elevation
                    ? "Only an administrator may write " + place + ": use a folder of the user's own (the app's data folder, "
                            + "NativeStore.directory(app), or the user's Documents), or run the app as administrator."
                    : "The system refused access to " + place + ": it may be read-only, open in another program, or another "
                            + "user's. Choose another place, or give this user access to it.";
            return new PermissionException(what + ": access denied", hint, elevation, 5, error);
        }
        Path file = fileInTheWay(path);
        String hint = switch (error) {
            case FileSystemException e when file != null ->
                    file + " is a file where a folder should be: choose another path (or move that file).";
            case NoSuchFileException e -> place + ", or a folder on the way to it, is not there: check the drive and the path.";
            case NotDirectoryException e -> "Part of " + place + " is a file where a folder should be: choose another path.";
            case FileAlreadyExistsException e -> "Part of " + place + " is a file where a folder should be (or the other way round): choose another path.";
            case FileSystemException e when detail.toLowerCase(Locale.ROOT).contains("not enough space") ->
                    "The disk is full: free some space, or choose another drive.";
            default -> "The system could not do it (" + detail + "): check " + place + ".";
        };
        return new SystemException(what + ": " + detail, hint, null, error);
    }
}
