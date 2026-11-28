package io.joshuasalcedo.swaswa.ffi;

import java.util.List;

/**
 * How much memory the app uses ({@link App#memory()}): Java's heap, this process (Java, its native library), and each
 * process of the browser engine, which is usually most of it (WebView2 on Windows: its browser, a renderer per site or
 * so, the GPU's, utilities). In bytes.
 *
 * @param heapUsed what Java's objects take now
 * @param heapMax  the most Java's heap may grow to ({@code -Xmx}); fixed when the JVM starts
 * @param processes this process first ({@code host}), then the engine's
 */
public record Memory(long heapUsed, long heapMax, List<Process> processes) {

    /**
     * One process: its id, its kind ({@code host}, {@code browser}, {@code renderer}, {@code gpu}, {@code utility},
     * {@code sandbox-helper}, {@code other}), what it has in RAM now, and what only it uses (in RAM or paged out).
     */
    public record Process(long pid, String kind, long resident, long privateBytes) {}

    public Memory {
        processes = List.copyOf(processes);
    }

    /** What the app costs the system: the private memory of this process and of the engine's. */
    public long total() {
        return processes.stream().mapToLong(Process::privateBytes).sum();
    }

    /** The browser engine's part of {@link #total()}. */
    public long engine() {
        return processes.stream().filter(p -> !p.kind().equals("host")).mapToLong(Process::privateBytes).sum();
    }

    /** This process (Java and the native library). */
    public Process host() {
        return processes.stream().filter(p -> p.kind().equals("host")).findFirst().orElseThrow();
    }

    /** The processes of one kind, e.g. {@code renderer}. */
    public List<Process> of(String kind) {
        return processes.stream().filter(p -> p.kind().equals(kind)).toList();
    }
}
