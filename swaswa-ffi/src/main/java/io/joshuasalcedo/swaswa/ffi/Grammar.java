package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.internal.Json;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spelling and grammar, offline, in Java: Harper (crates/grammar) in the native library, about a millisecond a
 * message. The same checker runs in the page as WebAssembly (the kit's GrammarTextArea); this one is for what Java
 * checks itself: text before it is saved or sent, in tests, on a server.
 * <pre>{@code
 * for (Grammar.Mistake m : Grammar.of("en-GB").check(text)) {
 *     System.out.println(m.found(text) + ": " + m.message() + " -> " + m.suggestions());
 * }
 * }</pre>
 */
public final class Grammar {

    /**
     * One thing to fix: where it is ({@code from} inclusive, {@code to} exclusive, as Java's {@link String} counts),
     * which rule found it, its kind (Spelling, Grammar, Punctuation, Capitalization, Style...), what it says, and
     * what could go there (each the whole text for {@code from..to}; an empty one removes it).
     */
    public record Mistake(int from, int to, String rule, String kind, String message, List<String> suggestions) {

        public Mistake {
            suggestions = List.copyOf(suggestions);
        }

        /** The words it is about, in {@code text}. */
        public String found(String text) {
            return text.substring(from, to);
        }

        public boolean spelling() {
            return "Spelling".equals(kind);
        }
    }

    private static final Linker LINKER = Linker.nativeLinker();
    private static final ValueLayout.OfLong U64 = ValueLayout.JAVA_LONG;
    private static final MethodHandle NEW = handle("wv_grammar_new", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, U64));
    private static final MethodHandle CHECK = handle("wv_grammar_check",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FREE = handle("wv_store_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, U64));
    private static final Map<String, Grammar> CHECKERS = new ConcurrentHashMap<>();

    private final String dialect;
    private final MemorySegment checker;

    private Grammar(String dialect, MemorySegment checker) {
        this.dialect = dialect;
        this.checker = checker;
    }

    /**
     * The checker for a dialect: en-US, en-GB, en-AU, en-CA or en-IN (anything else is en-US). Made once (its
     * dictionary takes a moment) and kept for the app's life; use it from any thread.
     */
    public static Grammar of(String dialect) {
        String tag = dialect == null || dialect.isBlank() ? "en-US" : dialect;
        return CHECKERS.computeIfAbsent(tag, Grammar::open);
    }

    /** American English. */
    public static Grammar english() {
        return of("en-US");
    }

    public String dialect() {
        return dialect;
    }

    /** The mistakes in {@code text}, in the order they appear. */
    public List<Mistake> check(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) return List.of();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String json;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = arena.allocate(Math.max(bytes.length, 1));
            MemorySegment.copy(bytes, 0, in, ValueLayout.JAVA_BYTE, 0, bytes.length);
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment len = arena.allocate(U64);
            int r = (int) CHECK.invokeExact(checker, in, (long) bytes.length, out, len);
            if (r != 1) throw new IllegalStateException("the grammar checker refused the text");
            MemorySegment ptr = out.get(ValueLayout.ADDRESS, 0);
            long n = len.get(U64, 0);
            json = new String(ptr.reinterpret(n).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
            FREE.invokeExact(ptr, n);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_grammar_check failed", e);
        }
        return parse(json);
    }

    /** {@code text} with {@code mistake} fixed by {@code suggestion}. */
    public static String apply(String text, Mistake mistake, String suggestion) {
        return text.substring(0, mistake.from()) + suggestion + text.substring(mistake.to());
    }

    /** {@code text} with each mistake fixed by its first suggestion (those with none left as they are). */
    public String fix(String text) {
        List<Mistake> found = check(text);
        StringBuilder out = new StringBuilder(text);
        for (int i = found.size() - 1; i >= 0; i--) {
            Mistake m = found.get(i);
            if (!m.suggestions().isEmpty() && (i == found.size() - 1 || m.to() <= found.get(i + 1).from())) {
                out.replace(m.from(), m.to(), m.suggestions().getFirst());
            }
        }
        return out.toString();
    }

    static List<Mistake> parse(String json) {
        List<Mistake> mistakes = new ArrayList<>();
        if (Json.parse(json) instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                List<String> suggestions = new ArrayList<>();
                if (m.get("suggestions") instanceof List<?> s) for (Object x : s) suggestions.add(String.valueOf(x));
                mistakes.add(new Mistake(((Number) m.get("from")).intValue(), ((Number) m.get("to")).intValue(),
                        String.valueOf(m.get("rule")), String.valueOf(m.get("kind")), String.valueOf(m.get("message")), suggestions));
            }
        }
        return List.copyOf(mistakes);
    }

    private static Grammar open(String tag) {
        byte[] bytes = tag.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = arena.allocate(bytes.length);
            MemorySegment.copy(bytes, 0, in, ValueLayout.JAVA_BYTE, 0, bytes.length);
            MemorySegment checker = (MemorySegment) NEW.invokeExact(in, (long) bytes.length);
            if (checker.equals(MemorySegment.NULL)) throw new IllegalStateException("the grammar checker did not start");
            return new Grammar(tag, checker);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_grammar_new failed", e);
        }
    }

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }
}
