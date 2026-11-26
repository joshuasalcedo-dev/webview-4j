package io.joshuasalcedo.webview4j.explorer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles Java sources in memory with the JDK's compiler, against the app's class path, into a class loader of their
 * own: the stories, each time they are saved or edited in the explorer. Nothing is written to disk.
 */
final class Compiler {

    /** The classes, or what kept them from compiling. */
    record Result(ClassLoader loader, List<String> classNames, String errors) {
        boolean ok() {
            return errors == null;
        }
    }

    private final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
    private final StandardJavaFileManager files;
    private final ClassLoader parent;
    private final String packagePrefix;
    private final String classPath = System.getProperty("java.class.path", "");

    /** Classes of {@code packageName} are loaded from what was compiled first, then from {@code parent}. */
    Compiler(ClassLoader parent, String packageName) {
        this.parent = parent;
        this.packagePrefix = packageName + ".";
        this.files = javac == null ? null : javac.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8);
    }

    boolean available() {
        return javac != null;
    }

    /** Compiles {@code sources} (file name -> its text). One compile at a time: the compiler is not shared. */
    synchronized Result compile(Map<String, String> sources) {
        if (javac == null) return new Result(null, List.of(), "No Java compiler: run the explorer with a JDK, not a JRE.");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classes = new HashMap<>();
        JavaFileManager memory = new ForwardingJavaFileManager<>(files) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
                return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                    @Override
                    public OutputStream openOutputStream() {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        classes.put(className, out);
                        return out;
                    }
                };
            }
        };
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, text) -> units.add(new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        }));
        List<String> options = List.of("-classpath", classPath + File.pathSeparator, "-proc:none", "-g", "-nowarn", "-parameters");
        boolean compiled = Boolean.TRUE.equals(javac.getTask(null, memory, diagnostics, options, null, units).call());
        if (!compiled) return new Result(null, List.of(), errors(diagnostics));

        Map<String, byte[]> bytes = new HashMap<>();
        classes.forEach((name, out) -> bytes.put(name, out.toByteArray()));
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // child first for the compiled package: the app's own (older) copies of the stories are not used
                synchronized (getClassLoadingLock(name)) {
                    Class<?> found = findLoadedClass(name);
                    if (found == null && bytes.containsKey(name)) found = findClass(name);
                    if (found == null) return super.loadClass(name, resolve);
                    if (resolve) resolveClass(found);
                    return found;
                }
            }

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] b = bytes.get(name);
                if (b == null) throw new ClassNotFoundException(name);
                return defineClass(name, b, 0, b.length);
            }
        };
        List<String> names = bytes.keySet().stream().filter(n -> n.startsWith(packagePrefix)).sorted().toList();
        return new Result(loader, names, null);
    }

    private static String errors(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> out = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR) continue;
            String file = d.getSource() == null ? "" : d.getSource().getName().replaceFirst("^.*/", "");
            out.add(file + (d.getLineNumber() > 0 ? ":" + d.getLineNumber() : "") + ": " + d.getMessage(Locale.ROOT));
        }
        return out.isEmpty() ? "It did not compile." : String.join("\n\n", out);
    }
}
