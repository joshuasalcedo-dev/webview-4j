package io.joshuasalcedo.webview4j.spring.client;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiException;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * The window of a {@link WebViewApplication}, made from the {@code @HttpExchange} interfaces it imports: a side bar of
 * the interfaces and their methods; for each method a form of its parameters (a {@code @RequestBody} record as a
 * field per component), checked before it is sent (the type of each value, then the Jakarta Validation annotations),
 * then sent through Spring's client, and its answer shown. The app's own {@link Screen} beans come first in the side
 * bar.
 */
public final class ClientScreen implements Screen {

    /** One page: an app's own screen, or one method of one client. */
    private record Page(String key, String section, String label, Screen screen, Object client, Method method, String http) {}

    /** One field of a method's form: where its text is kept, what it is called, what it becomes. */
    private record Field(String key, String label, Type type, boolean required) {}

    private final List<Page> pages = new ArrayList<>();
    private final Checker checker;
    private final String title;
    private volatile UiWindow window;
    private String selected;
    private final Map<String, String> text = new HashMap<>();
    private final Map<String, Map<String, String>> errors = new HashMap<>();
    private final Map<String, Object> answers = new HashMap<>();
    private final Set<String> busy = new HashSet<>();

    /**
     * @param title    the window's title
     * @param screens  the app's own screens, by the name they show under
     * @param clients  the HTTP service clients: each interface and its client
     * @param checker  Jakarta Validation of a call's arguments; null without it
     */
    ClientScreen(String title, Map<String, Screen> screens, Map<Class<?>, Object> clients, Checker checker) {
        this.title = title;
        this.checker = checker;
        screens.forEach((name, screen) -> pages.add(new Page("screen:" + name, "Pages", name, screen, null, null, null)));
        clients.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getSimpleName())).forEach(entry -> {
            Class<?> type = entry.getKey();
            HttpExchange base = AnnotatedElementUtils.findMergedAnnotation(type, HttpExchange.class);
            String root = base == null ? "" : first(base.url(), base.value());
            List<Method> methods = new ArrayList<>(List.of(type.getMethods()));
            methods.removeIf(m -> m.isDefault() || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class);
            methods.sort(Comparator.comparing(Method::getName));
            for (Method method : methods) {
                HttpExchange exchange = AnnotatedElementUtils.findMergedAnnotation(method, HttpExchange.class);
                if (exchange == null) continue;
                String http = (exchange.method().isEmpty() ? "GET" : exchange.method()) + " " + join(root, first(exchange.url(), exchange.value()));
                pages.add(new Page(type.getName() + "#" + method.getName() + method.getParameterCount(), Values.words(type.getSimpleName()),
                        Values.words(method.getName()), null, entry.getValue(), method, http));
            }
        });
        if (!pages.isEmpty()) selected = pages.getFirst().key();
    }

    void attach(UiWindow window) {
        this.window = window;
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b == null ? "" : b;
    }

    private static String join(String root, String path) {
        if (root.isEmpty()) return path.isEmpty() ? "/" : path;
        if (path.isEmpty()) return root;
        return root.replaceAll("/+$", "") + "/" + path.replaceAll("^/+", "");
    }

    // ---- the screen ----

    @Override
    public synchronized Component<?> render() {
        if (pages.isEmpty()) {
            return frame(column(Ui.title(title),
                    text("No @HttpExchange interfaces were imported and there are no Screen beans: add "
                            + "@ImportHttpServices(basePackageClasses = YourApi.class) to the application class.").tone(Tone.SECONDARY))
                    .padding(24).gap(12)).titleBar(titleBar(title));
        }
        Map<String, List<Component<?>>> sections = new LinkedHashMap<>();
        for (Page page : pages) {
            sections.computeIfAbsent(page.section(), s -> new ArrayList<>()).add(
                    sidebarItem(page.label()).key(page.key()).selected(page.key().equals(selected)).onSelect(() -> selected = page.key()));
        }
        Sidebar sidebar = sidebar(sections.entrySet().stream()
                .map(s -> sidebarSection(s.getKey(), s.getValue().toArray(Component<?>[]::new)))
                .toArray(Component<?>[]::new));
        Page page = pages.stream().filter(p -> p.key().equals(selected)).findFirst().orElse(pages.getFirst());
        Component<?> content = page.screen() != null ? page.screen().render() : method(page);
        return frame(split(sidebar, content))
                .titleBar(titleBar(title))
                .statusBar(statusBar(statusItem(page.http() != null ? page.http() : page.label()), spacer(),
                        statusItem(pages.size() + " pages")));
    }

    private Component<?> method(Page page) {
        List<Field> fields = fields(page.method());
        Map<String, String> wrong = errors.getOrDefault(page.key(), Map.of());
        List<Component<?>> inputs = new ArrayList<>();
        for (Field field : fields) inputs.add(input(page, field, wrong.get(field.key())));
        boolean sending = busy.contains(page.key());
        List<Component<?>> parts = new ArrayList<>();
        parts.add(text(page.label()).variant(TextVariant.LARGE_TITLE));
        parts.add(text(page.http()).mono().tone(Tone.SECONDARY));
        if (!inputs.isEmpty()) parts.add(surface("Input", inputs.toArray(Component<?>[]::new)));
        if (wrong.containsKey("")) parts.add(text(wrong.get("")).tone(Tone.ERROR));
        parts.add(row(button(sending ? "Sending…" : "Send").accent().disabled(sending).onClick(() -> send(page, fields))));
        if (answers.containsKey(page.key())) parts.add(surface("Answer", answer(answers.get(page.key()))));
        return column(parts).padding(24).gap(16);
    }

    private Component<?> input(Page page, Field field, String error) {
        Class<?> raw = Values.raw(field.type());
        String key = page.key() + "/" + field.key();
        String label = field.label() + (field.required() ? "" : " (optional)");
        if (raw == boolean.class || raw == Boolean.class) {
            return toggle(label).key(key).checked(Boolean.parseBoolean(text.getOrDefault(key, "false")))
                    .onChange(v -> text.put(key, String.valueOf(v)));
        }
        if (raw.isEnum()) {
            Map<String, String> options = new LinkedHashMap<>();
            if (!field.required()) options.put("", "—");
            for (Object constant : raw.getEnumConstants()) options.put(((Enum<?>) constant).name(), Values.words(((Enum<?>) constant).name().toLowerCase()));
            String first = options.keySet().iterator().next();
            text.putIfAbsent(key, first);
            return select(label, options).key(key).value(text.get(key)).onChange(v -> text.put(key, v)).width(320);
        }
        boolean many = Collection.class.isAssignableFrom(raw);
        if (many) {
            return textArea(label + ", one per line").key(key).rows(3).value(text.getOrDefault(key, "")).onChange(v -> text.put(key, v));
        }
        TextField f = textField(label).key(key).value(text.getOrDefault(key, "")).onChange(v -> text.put(key, v));
        if (error != null) f.error(error);
        else f.hint(hint(raw));
        return f;
    }

    private static String hint(Class<?> raw) {
        if (raw == java.util.UUID.class) return "An id";
        if (raw == java.time.LocalDate.class) return "A date, as 2026-11-25";
        if (raw.isPrimitive() || Number.class.isAssignableFrom(raw)) return "A number";
        if (raw == String.class) return null;
        return Values.words(raw.getSimpleName());
    }

    /** The method's fields: its parameters, a record's components (one level of records within records too). */
    private List<Field> fields(Method method) {
        List<Field> fields = new ArrayList<>();
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            String name = name(p, i);
            Class<?> raw = p.getType();
            if (raw.isRecord() && !Values.simple(raw)) {
                for (RecordComponent c : raw.getRecordComponents()) {
                    if (c.getType().isRecord() && !Values.simple(c.getType())) {
                        for (RecordComponent inner : c.getType().getRecordComponents()) {
                            fields.add(new Field(name + "." + c.getName() + "." + inner.getName(),
                                    Values.words(c.getName()) + ": " + Values.words(inner.getName()), inner.getGenericType(), required(inner)));
                        }
                    } else {
                        fields.add(new Field(name + "." + c.getName(), Values.words(c.getName()), c.getGenericType(), required(c)));
                    }
                }
            } else {
                fields.add(new Field(name, Values.words(name), p.getParameterizedType(), required(p)));
            }
        }
        return fields;
    }

    private static boolean required(java.lang.reflect.AnnotatedElement element) {
        for (var a : element.getAnnotations()) {
            String n = a.annotationType().getSimpleName();
            if (n.equals("NotNull") || n.equals("NotBlank") || n.equals("NotEmpty")) return true;
        }
        if (element instanceof Parameter p) {
            PathVariable path = p.getAnnotation(PathVariable.class);
            RequestParam param = p.getAnnotation(RequestParam.class);
            if (path != null) return path.required();
            if (param != null) return param.required();
            return p.getType().isPrimitive();
        }
        if (element instanceof RecordComponent c) return c.getType().isPrimitive();
        return false;
    }

    /** What a parameter is called: its annotation's name, its compiled name, or arg0... */
    static String name(Parameter p, int index) {
        PathVariable path = p.getAnnotation(PathVariable.class);
        if (path != null && !first(path.name(), path.value()).isEmpty()) return first(path.name(), path.value());
        RequestParam param = p.getAnnotation(RequestParam.class);
        if (param != null && !first(param.name(), param.value()).isEmpty()) return first(param.name(), param.value());
        RequestHeader header = p.getAnnotation(RequestHeader.class);
        if (header != null && !first(header.name(), header.value()).isEmpty()) return first(header.name(), header.value());
        if (p.isNamePresent()) return p.getName();
        return p.getAnnotation(RequestBody.class) != null ? Values.words(p.getType().getSimpleName()).toLowerCase().replace(' ', '-') : "arg" + index;
    }

    // ---- sending ----

    private void send(Page page, List<Field> fields) {
        Map<String, String> wrong = new LinkedHashMap<>();
        Object[] args = arguments(page, wrong);
        if (wrong.isEmpty() && checker != null) wrong.putAll(checker.check(page.client(), page.method(), args, ClientScreen::name));
        if (!wrong.isEmpty()) {
            errors.put(page.key(), wrong);
            // A problem not on any field shown here (a list's item, a field of a deeper record): say it above Send.
            List<String> elsewhere = wrong.entrySet().stream()
                    .filter(e -> fields.stream().noneMatch(f -> f.key().equals(e.getKey())))
                    .map(e -> (e.getKey().isEmpty() ? "" : e.getKey() + ": ") + e.getValue()).toList();
            if (!elsewhere.isEmpty()) wrong.put("", String.join("; ", elsewhere));
            return;
        }
        errors.remove(page.key());
        busy.add(page.key());
        Thread.ofVirtual().name("webview4j-call").start(() -> {
            Object answer;
            try {
                Object result = page.method().invoke(page.client(), args);
                answer = result == null ? Done.DONE : result;
            } catch (InvocationTargetException e) {
                answer = e.getCause();
            } catch (ReflectiveOperationException | RuntimeException e) {
                answer = e;
            }
            Object shown = answer;
            synchronized (this) {
                answers.put(page.key(), shown);
                busy.remove(page.key());
            }
            UiWindow w = window;
            if (w != null) {
                if (shown instanceof Throwable t) w.notify("The server said no", said(t), UiException.Level.ERROR);
                else w.refresh();
            }
        });
    }

    private enum Done { DONE }

    /** The call's arguments from the fields' text; what does not fit goes in {@code wrong}, by field. */
    private Object[] arguments(Page page, Map<String, String> wrong) {
        Parameter[] parameters = page.method().getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            String name = name(p, i);
            args[i] = p.getType().isRecord() && !Values.simple(p.getType())
                    ? record(page, p.getType(), name, wrong)
                    : value(page, name, p.getParameterizedType(), wrong);
        }
        return args;
    }

    private Object record(Page page, Class<?> type, String prefix, Map<String, String> wrong) {
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent c = components[i];
            types[i] = c.getType();
            String key = prefix + "." + c.getName();
            values[i] = c.getType().isRecord() && !Values.simple(c.getType())
                    ? record(page, c.getType(), key, wrong)
                    : value(page, key, c.getGenericType(), wrong);
        }
        if (!wrong.isEmpty()) return null;
        try {
            var constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            // The record's own check (its compact constructor) said no.
            wrong.put(prefix, e.getCause() != null && e.getCause().getMessage() != null ? e.getCause().getMessage() : "Not valid");
            return null;
        } catch (ReflectiveOperationException e) {
            wrong.put(prefix, "Can't be made from the form: " + e.getMessage());
            return null;
        }
    }

    private Object value(Page page, String key, Type type, Map<String, String> wrong) {
        try {
            return Values.convert(text.get(page.key() + "/" + key), type);
        } catch (Values.Unfit e) {
            wrong.put(key, e.getMessage());
            return null;
        } catch (RuntimeException e) {
            wrong.put(key, e.getMessage() == null ? "Not valid" : e.getMessage());
            return null;
        }
    }

    /** What the server (or the way to it) said, for people. */
    private static String said(Throwable t) {
        if (t instanceof org.springframework.web.client.RestClientResponseException r) {
            String body = r.getResponseBodyAsString();
            return r.getStatusCode().value() + " " + r.getStatusText() + (body.isBlank() ? "" : ": " + (body.length() > 300 ? body.substring(0, 300) + "…" : body));
        }
        if (t instanceof org.springframework.web.client.ResourceAccessException) return "The server can't be reached: " + t.getMessage();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    // ---- answers ----

    private Component<?> answer(Object answer) {
        if (answer == Done.DONE) return text("Done.").tone(Tone.SUCCESS);
        if (answer instanceof Throwable t) return text(said(t)).tone(Tone.ERROR);
        if (answer instanceof org.springframework.http.ResponseEntity<?> r) {
            return column(text(r.getStatusCode().toString()).tone(Tone.SECONDARY), r.getBody() == null ? text("No body.") : answer(r.getBody()));
        }
        if (answer instanceof Optional<?> o) return o.isPresent() ? answer(o.get()) : text("Nothing.").tone(Tone.SECONDARY);
        if (answer instanceof Collection<?> items) {
            if (items.isEmpty()) return text("None.").tone(Tone.SECONDARY);
            List<Component<?>> rows = new ArrayList<>();
            int i = 0;
            for (Object item : items) {
                rows.add(listItem(heading(item)).key(i++).subtitle(summary(item)));
                if (i >= 500) break;
            }
            return column(text(items.size() + (items.size() == 1 ? " item" : " items")).tone(Tone.SECONDARY), list("Answer", rows));
        }
        if (answer.getClass().isRecord()) {
            List<Component<?>> rows = new ArrayList<>();
            for (RecordComponent c : answer.getClass().getRecordComponents()) {
                rows.add(row(text(Values.words(c.getName())).tone(Tone.SECONDARY).width(180), text(Values.text(read(answer, c)))));
            }
            return column(rows).gap(6);
        }
        if (answer instanceof Map<?, ?> map) {
            List<Component<?>> rows = new ArrayList<>();
            map.forEach((k, v) -> rows.add(row(text(String.valueOf(k)).tone(Tone.SECONDARY).width(180), text(Values.text(v)))));
            return column(rows).gap(6);
        }
        return text(String.valueOf(answer));
    }

    private static Object read(Object record, RecordComponent c) {
        try {
            Method accessor = c.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "?";
        }
    }

    /** An item's title in a list: its name, title or email, or its first text. */
    private static String heading(Object item) {
        if (item == null) return "—";
        if (!item.getClass().isRecord()) return String.valueOf(item);
        RecordComponent[] components = item.getClass().getRecordComponents();
        for (String preferred : List.of("name", "displayName", "title", "label", "email", "username", "id")) {
            for (RecordComponent c : components) if (c.getName().equals(preferred)) return Values.text(read(item, c));
        }
        return components.length == 0 ? item.toString() : Values.text(read(item, components[0]));
    }

    private static String summary(Object item) {
        if (item == null || !item.getClass().isRecord()) return null;
        String heading = heading(item);
        List<String> parts = new ArrayList<>();
        for (RecordComponent c : item.getClass().getRecordComponents()) {
            String value = Values.text(read(item, c));
            if (!value.isEmpty() && !value.equals(heading) && parts.size() < 4) parts.add(Values.words(c.getName()) + ": " + value);
        }
        return String.join(" · ", parts);
    }

    /** Checks a call's arguments before it is sent; its problems by field key ("invitation.email"). */
    interface Checker {
        Map<String, String> check(Object client, Method method, Object[] args, java.util.function.BiFunction<Parameter, Integer, String> names);
    }
}
