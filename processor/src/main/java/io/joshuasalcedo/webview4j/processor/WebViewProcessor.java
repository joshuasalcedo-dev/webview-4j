package io.joshuasalcedo.webview4j.processor;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

/**
 * The webview-4j annotation processor. For every {@code @WebViewController} it writes, next to the
 * controller, {@code <Controller>_Routes}: the route table {@code App.register(controller)} uses,
 * calling the controller's methods directly and reading each request into Java types without
 * reflection. Once all controllers are seen, it writes one TypeScript client for the page: an
 * interface per record, a union per enum, and an object of typed functions per controller.
 *
 * <p>The TypeScript goes to the file named by {@code -Awebview4j.typescript=<path>}, or else to
 * {@code META-INF/webview4j/api.ts} in the class output.
 *
 * <p>Everything a route takes or returns must be something JSON can carry; anything else, a path
 * placeholder without a {@code @Path} parameter, or a body on a GET is a compile error.
 */
@SupportedAnnotationTypes(WebViewProcessor.CONTROLLER)
@SupportedOptions(WebViewProcessor.TYPESCRIPT_OPTION)
public final class WebViewProcessor extends AbstractProcessor {

    static final String TYPESCRIPT_OPTION = "webview4j.typescript";
    static final String ANNOTATIONS = "io.joshuasalcedo.webview4j.annotation.";
    static final String CONTROLLER = ANNOTATIONS + "WebViewController";
    private static final String CORE = "io.joshuasalcedo.webview4j.";
    private static final Map<String, String> METHODS = new LinkedHashMap<>();
    static {
        METHODS.put("Get", "GET");
        METHODS.put("Post", "POST");
        METHODS.put("Put", "PUT");
        METHODS.put("Patch", "PATCH");
        METHODS.put("Delete", "DELETE");
    }
    private static final Pattern LITERAL = Pattern.compile("[A-Za-z0-9\\-_.~]+");
    private static final Pattern PARAMETER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Set<String> TS_RESERVED = Set.of("arguments", "await", "break", "case", "catch", "class", "const",
            "continue", "debugger", "default", "delete", "do", "else", "enum", "eval", "export", "extends", "false",
            "finally", "for", "function", "if", "implements", "import", "in", "instanceof", "interface", "let", "new",
            "null", "package", "private", "protected", "public", "return", "static", "super", "switch", "this", "throw",
            "true", "try", "typeof", "var", "void", "while", "with", "yield", "p", "request");

    private enum Kind { PATH, QUERY, BODY, VIEW, REQUEST }

    private record Param(Kind kind, String binding, String javaName, TypeMirror type) {}

    private record RouteModel(ExecutableElement method, String httpMethod, String pattern, List<Param> params,
                              TypeMirror result) {}

    private record ControllerModel(TypeElement type, String tsName, List<RouteModel> routes) {}

    private final List<ControllerModel> controllers = new ArrayList<>();
    private boolean failed;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement annotation = processingEnv.getElementUtils().getTypeElement(CONTROLLER);
        if (annotation != null) {
            for (Element element : round.getElementsAnnotatedWith(annotation)) {
                ControllerModel controller = read((TypeElement) element);
                if (controller != null) {
                    writeRoutes(controller);
                    controllers.add(controller);
                }
            }
        }
        if (round.processingOver() && !controllers.isEmpty() && !failed) writeTypeScript();
        return true;
    }

    // ---- reading a controller ----

    private ControllerModel read(TypeElement type) {
        if (type.getKind() != ElementKind.CLASS || !type.getTypeParameters().isEmpty()) {
            return error(type, "a @WebViewController is a class without type parameters");
        }
        if (type.getNestingKind() == NestingKind.LOCAL || type.getNestingKind() == NestingKind.ANONYMOUS
                || (type.getNestingKind() == NestingKind.MEMBER && !type.getModifiers().contains(Modifier.STATIC))) {
            return error(type, "a @WebViewController is a top-level or static nested class");
        }
        AnnotationMirror controller = annotation(type, "WebViewController");
        String base = string(controller, "value");
        String tsName = string(controller, "name");
        if (tsName.isEmpty()) {
            String simple = type.getSimpleName().toString();
            String stem = simple.endsWith("Controller") && simple.length() > "Controller".length()
                    ? simple.substring(0, simple.length() - "Controller".length()) : simple;
            tsName = Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
        }
        if (!SourceVersion.isIdentifier(tsName) || TS_RESERVED.contains(tsName)) {
            return error(type, "'" + tsName + "' cannot name this controller in TypeScript: set @WebViewController(name = ...)");
        }
        List<RouteModel> routes = new ArrayList<>();
        boolean ok = true;
        for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
            for (Map.Entry<String, String> verb : METHODS.entrySet()) {
                AnnotationMirror mapping = annotation(method, verb.getKey());
                if (mapping == null) continue;
                RouteModel route = route(method, verb.getValue(), join(base, string(mapping, "value")));
                if (route == null) ok = false;
                else routes.add(route);
            }
        }
        if (!ok) return null;
        if (routes.isEmpty()) return error(type, "a @WebViewController needs at least one @Get, @Post, @Put, @Patch or @Delete method");
        return new ControllerModel(type, tsName, routes);
    }

    private RouteModel route(ExecutableElement method, String httpMethod, String pattern) {
        if (method.getModifiers().contains(Modifier.STATIC) || method.getModifiers().contains(Modifier.PRIVATE)) {
            return error(method, "a route method is an instance method that is not private");
        }
        if (!method.getTypeParameters().isEmpty()) return error(method, "a route method has no type parameters");
        Set<String> placeholders = new LinkedHashSet<>();
        for (String segment : pattern.equals("/") ? new String[0] : pattern.substring(1).split("/", -1)) {
            Matcher parameter = PARAMETER.matcher(segment);
            if (parameter.matches()) placeholders.add(parameter.group(1));
            else if (!LITERAL.matcher(segment).matches()) {
                return error(method, "bad path segment '" + segment + "' in " + pattern
                        + ": use letters, digits, - _ . ~ or {name}");
            }
        }
        List<Param> params = new ArrayList<>();
        Set<String> bound = new LinkedHashSet<>();
        boolean body = false;
        for (VariableElement parameter : method.getParameters()) {
            TypeMirror type = parameter.asType();
            String javaName = parameter.getSimpleName().toString();
            AnnotationMirror path = annotation(parameter, "Path");
            AnnotationMirror query = annotation(parameter, "Query");
            AnnotationMirror bodyAnnotation = annotation(parameter, "Body");
            int bindings = (path != null ? 1 : 0) + (query != null ? 1 : 0) + (bodyAnnotation != null ? 1 : 0);
            if (bindings > 1) return error(parameter, "a parameter is bound once: @Path, @Query or @Body");
            if (path != null || query != null) {
                String name = string(path != null ? path : query, "value");
                if (name.isEmpty()) name = javaName;
                if (!textType(type)) {
                    return error(parameter, (path != null ? "@Path" : "@Query") + " takes text, a number, a boolean or an enum, not " + type);
                }
                if (path != null) {
                    if (!placeholders.contains(name)) return error(parameter, "no {" + name + "} in " + pattern);
                    bound.add(name);
                    params.add(new Param(Kind.PATH, name, javaName, type));
                } else {
                    params.add(new Param(Kind.QUERY, name, javaName, type));
                }
            } else if (bodyAnnotation != null) {
                if (body) return error(parameter, "a route has at most one @Body");
                if (httpMethod.equals("GET")) return error(parameter, "a GET route has no body");
                if (!jsonType(type, parameter, new LinkedHashSet<>())) return null;
                body = true;
                params.add(new Param(Kind.BODY, "body", javaName, type));
            } else if (named(type, CORE + "View")) {
                params.add(new Param(Kind.VIEW, null, javaName, type));
            } else if (named(type, CORE + "RouteRequest")) {
                params.add(new Param(Kind.REQUEST, null, javaName, type));
            } else {
                return error(parameter, "bind this parameter with @Path, @Query or @Body, or take View or RouteRequest");
            }
        }
        for (String placeholder : placeholders) {
            if (!bound.contains(placeholder)) return error(method, "{" + placeholder + "} in " + pattern + " needs a @Path parameter");
        }
        TypeMirror result = method.getReturnType();
        TypeMirror async = typeArgument(result, "java.util.concurrent.CompletionStage");
        if (async != null) result = async;
        if (result.getKind() != TypeKind.VOID && !named(result, "java.lang.Void") && !jsonType(result, method, new LinkedHashSet<>())) return null;
        return new RouteModel(method, httpMethod, pattern, params, result);
    }

    private static String join(String base, String path) {
        String joined = ("/" + base + "/" + path).replaceAll("/+", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    // ---- types ----

    private boolean textType(TypeMirror type) {
        return switch (type.getKind()) {
            case LONG, INT, DOUBLE, BOOLEAN -> true;
            case DECLARED -> named(type, "java.lang.String") || named(type, "java.lang.Long") || named(type, "java.lang.Integer")
                    || named(type, "java.lang.Double") || named(type, "java.lang.Boolean") || isEnum(type);
            default -> false;
        };
    }

    /** True if JSON can carry {@code type}; otherwise reports why at {@code at}. */
    private boolean jsonType(TypeMirror type, Element at, Set<String> seen) {
        switch (type.getKind()) {
            case LONG, INT, DOUBLE, BOOLEAN:
                return true;
            case DECLARED:
                break;
            default:
                error(at, type + " cannot be carried as JSON: use text, numbers (long, int, double), booleans, enums, records, List, Map<String, ...> or Optional");
                return false;
        }
        if (textType(type) || named(type, "java.lang.Object")) return true;
        TypeMirror element = typeArgument(type, "java.util.List");
        if (element != null && named(erasure(type), "java.util.List")) return jsonType(element, at, seen);
        if (named(erasure(type), "java.util.Optional")) return jsonType(typeArgument(type, "java.util.Optional"), at, seen);
        if (named(erasure(type), "java.util.Map")) {
            List<? extends TypeMirror> arguments = ((DeclaredType) type).getTypeArguments();
            if (arguments.size() != 2 || !named(arguments.get(0), "java.lang.String")) {
                error(at, type + ": a JSON object's keys are text, use Map<String, ...>");
                return false;
            }
            return jsonType(arguments.get(1), at, seen);
        }
        TypeElement declared = (TypeElement) ((DeclaredType) type).asElement();
        if (declared.getKind() == ElementKind.RECORD) {
            if (!declared.getTypeParameters().isEmpty()) {
                error(at, type + ": records with type parameters are not supported");
                return false;
            }
            if (!seen.add(declared.getQualifiedName().toString())) return true;
            for (RecordComponentElement component : declared.getRecordComponents()) {
                if (!jsonType(component.asType(), at, seen)) return false;
            }
            return true;
        }
        error(at, type + " cannot be carried as JSON: use text, numbers (long, int, double), booleans, enums, records, List, Map<String, ...> or Optional");
        return false;
    }

    private boolean isEnum(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement().getKind() == ElementKind.ENUM;
    }

    private boolean isRecord(TypeMirror type) {
        return type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement().getKind() == ElementKind.RECORD;
    }

    private TypeMirror erasure(TypeMirror type) {
        return processingEnv.getTypeUtils().erasure(type);
    }

    private static boolean named(TypeMirror type, String name) {
        return type.getKind() == TypeKind.DECLARED
                && ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().contentEquals(name);
    }

    /** {@code type} seen as {@code generic<T>} (itself or a supertype), and its T; null if it is not one. */
    private TypeMirror typeArgument(TypeMirror type, String generic) {
        if (type.getKind() != TypeKind.DECLARED) return null;
        DeclaredType declared = (DeclaredType) type;
        if (named(declared, generic)) {
            return declared.getTypeArguments().size() == 1 ? declared.getTypeArguments().getFirst() : null;
        }
        for (TypeMirror supertype : processingEnv.getTypeUtils().directSupertypes(type)) {
            TypeMirror argument = typeArgument(supertype, generic);
            if (argument != null) return argument;
        }
        return null;
    }

    private static String qualified(TypeMirror type) {
        return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().toString();
    }

    // ---- the Java route table ----

    private void writeRoutes(ControllerModel controller) {
        TypeElement type = controller.type();
        String pkg = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String controllerName = type.getQualifiedName().toString();
        String simple = (pkg.isEmpty() ? controllerName : controllerName.substring(pkg.length() + 1)).replace('.', '_') + "_Routes";
        Map<String, String> readers = new TreeMap<>();
        Deque<TypeMirror> pending = new ArrayDeque<>();
        Map<String, TypeMirror> recordTypes = new LinkedHashMap<>();

        StringBuilder routes = new StringBuilder();
        for (RouteModel route : controller.routes()) {
            List<String> arguments = new ArrayList<>();
            for (Param param : route.params()) {
                arguments.add(switch (param.kind()) {
                    case VIEW -> "r.view()";
                    case REQUEST -> "r";
                    case PATH -> fromText(param.type(), "r.pathParam(\"" + param.binding() + "\")", "\"" + param.binding() + "\"", true);
                    case QUERY -> fromText(param.type(), "r.query(\"" + param.binding() + "\")", "\"" + param.binding() + "\"", false);
                    case BODY -> fromJson(param.type(), "r.json()", "\"body\"", 0, recordTypes, pending);
                });
            }
            String call = "c." + route.method().getSimpleName() + "(" + String.join(", ", arguments) + ")";
            boolean returnsNothing = route.method().getReturnType().getKind() == TypeKind.VOID;
            routes.append("                new " + CORE + "Route(" + CORE + "HttpMethod." + route.httpMethod() + ", \"" + route.pattern() + "\", ")
                    .append(returnsNothing ? "r -> { " + call + "; return null; }" : "r -> " + call)
                    .append("),\n");
        }
        while (!pending.isEmpty()) {
            TypeMirror record = pending.pop();
            String name = qualified(record);
            if (readers.containsKey(name)) continue;
            TypeElement element = (TypeElement) ((DeclaredType) record).asElement();
            StringBuilder reader = new StringBuilder();
            reader.append("    private static ").append(name).append(" ").append(readerName(name)).append("(Object value, String where) {\n")
                    .append("        java.util.Map<String, Object> object = " + CORE + "Values.object(value, where);\n")
                    .append("        return new ").append(name).append("(");
            List<String> fields = new ArrayList<>();
            for (RecordComponentElement component : element.getRecordComponents()) {
                String field = component.getSimpleName().toString();
                fields.add("\n                " + fromJson(component.asType(), "object.get(\"" + field + "\")", "where + \"." + field + "\"", 0, recordTypes, pending));
            }
            reader.append(String.join(",", fields)).append(");\n    }\n");
            readers.put(name, reader.toString());
        }

        StringBuilder source = new StringBuilder();
        source.append("// GENERATED by the webview-4j annotation processor from ").append(controllerName).append(". Do not edit.\n");
        if (!pkg.isEmpty()) source.append("package ").append(pkg).append(";\n\n");
        source.append("@javax.annotation.processing.Generated(\"io.joshuasalcedo.webview4j.processor.WebViewProcessor\")\n")
                .append("public final class ").append(simple).append(" implements " + CORE + "Routes<").append(controllerName).append("> {\n\n")
                .append("    @Override\n    public Class<").append(controllerName).append("> controller() {\n        return ").append(controllerName).append(".class;\n    }\n\n")
                .append("    @Override\n    public java.util.List<" + CORE + "Route> routes(").append(controllerName).append(" c) {\n")
                .append("        return java.util.List.of(\n")
                .append(routes.substring(0, routes.length() - 2)).append(");\n    }\n");
        for (String reader : readers.values()) source.append("\n").append(reader);
        source.append("}\n");
        try (Writer writer = processingEnv.getFiler().createSourceFile((pkg.isEmpty() ? "" : pkg + ".") + simple, type).openWriter()) {
            writer.write(source.toString());
        } catch (IOException e) {
            error(type, "could not write " + simple + ": " + e.getMessage());
        }
    }

    private static String readerName(String qualifiedRecord) {
        return "read_" + qualifiedRecord.replace('.', '_');
    }

    /** A Java expression reading text (a path segment or query value) as {@code type}. */
    private String fromText(TypeMirror type, String raw, String where, boolean required) {
        String values = CORE + "Values.";
        return switch (type.getKind()) {
            case LONG -> values + "parseLong(" + raw + ", " + where + ")";
            case INT -> values + "parseInt(" + raw + ", " + where + ")";
            case DOUBLE -> values + "parseDouble(" + raw + ", " + where + ")";
            case BOOLEAN -> values + "parseBoolean(" + raw + ", " + where + ")";
            default -> {
                String text = required ? values + "required(" + raw + ", " + where + ")" : raw;
                if (named(type, "java.lang.String")) yield text;
                if (named(type, "java.lang.Long")) yield values + "parseLongOrNull(" + text + ", " + where + ")";
                if (named(type, "java.lang.Integer")) yield values + "parseIntOrNull(" + text + ", " + where + ")";
                if (named(type, "java.lang.Double")) yield values + "parseDoubleOrNull(" + text + ", " + where + ")";
                if (named(type, "java.lang.Boolean")) yield values + "parseBooleanOrNull(" + text + ", " + where + ")";
                yield values + "parseEnum(" + qualified(type) + ".class, " + text + ", " + where + ")";
            }
        };
    }

    /** A Java expression reading the JSON value {@code value} as {@code type}. */
    private String fromJson(TypeMirror type, String value, String where, int depth, Map<String, TypeMirror> records, Deque<TypeMirror> pending) {
        String values = CORE + "Values.";
        String args = "(" + value + ", " + where + ")";
        switch (type.getKind()) {
            case LONG: return values + "longValue" + args;
            case INT: return values + "intValue" + args;
            case DOUBLE: return values + "doubleValue" + args;
            case BOOLEAN: return values + "booleanValue" + args;
            default: break;
        }
        if (named(type, "java.lang.String")) return values + "string" + args;
        if (named(type, "java.lang.Long")) return values + "longOrNull" + args;
        if (named(type, "java.lang.Integer")) return values + "intOrNull" + args;
        if (named(type, "java.lang.Double")) return values + "doubleOrNull" + args;
        if (named(type, "java.lang.Boolean")) return values + "booleanOrNull" + args;
        if (named(type, "java.lang.Object")) return value;
        if (isEnum(type)) return values + "enumValue(" + qualified(type) + ".class, " + value + ", " + where + ")";
        String e = "e" + depth;
        String w = "w" + depth;
        if (named(erasure(type), "java.util.List")) {
            TypeMirror element = typeArgument(type, "java.util.List");
            return values + "listOf(" + value + ", " + where + ", (" + e + ", " + w + ") -> " + fromJson(element, e, w, depth + 1, records, pending) + ")";
        }
        if (named(erasure(type), "java.util.Optional")) {
            TypeMirror element = typeArgument(type, "java.util.Optional");
            return values + "optional(" + value + ", " + where + ", (" + e + ", " + w + ") -> " + fromJson(element, e, w, depth + 1, records, pending) + ")";
        }
        if (named(erasure(type), "java.util.Map")) {
            TypeMirror element = ((DeclaredType) type).getTypeArguments().get(1);
            return values + "mapOf(" + value + ", " + where + ", (" + e + ", " + w + ") -> " + fromJson(element, e, w, depth + 1, records, pending) + ")";
        }
        String name = qualified(type);
        if (records.putIfAbsent(name, type) == null) pending.push(type);
        return readerName(name) + args;
    }

    // ---- the TypeScript client ----

    private void writeTypeScript() {
        Map<String, TypeElement> interfaces = new TreeMap<>();
        Map<String, TypeElement> enums = new TreeMap<>();
        StringBuilder clients = new StringBuilder();
        Set<String> names = new LinkedHashSet<>();
        for (ControllerModel controller : controllers) {
            if (!names.add(controller.tsName())) {
                error(controller.type(), "two controllers are named '" + controller.tsName() + "' in TypeScript: set @WebViewController(name = ...)");
                return;
            }
            clients.append("\n/** ").append(controller.type().getQualifiedName()).append(" */\n")
                    .append("export const ").append(controller.tsName()).append(" = {\n");
            for (RouteModel route : controller.routes()) {
                List<String> signature = new ArrayList<>();
                List<String> query = new ArrayList<>();
                String body = null;
                List<Param> visible = route.params().stream().filter(p -> p.kind() != Kind.VIEW && p.kind() != Kind.REQUEST).toList();
                int lastRequired = -1;
                for (int i = 0; i < visible.size(); i++) {
                    if (visible.get(i).kind() != Kind.QUERY || visible.get(i).type().getKind().isPrimitive()) lastRequired = i;
                }
                for (int i = 0; i < visible.size(); i++) {
                    Param param = visible.get(i);
                    String name = tsIdentifier(param.javaName());
                    String type = tsType(param.type(), interfaces, enums, controller.type());
                    if (type == null) return;
                    boolean optional = param.kind() == Kind.QUERY && !param.type().getKind().isPrimitive();
                    signature.add(optional && i > lastRequired ? name + "?: " + type + " | null" : name + ": " + type + (optional ? " | null" : ""));
                    if (param.kind() == Kind.QUERY) query.add(param.binding().equals(name) ? name : jsString(param.binding()) + ": " + name);
                    if (param.kind() == Kind.BODY) body = name;
                }
                String result = route.result().getKind() == TypeKind.VOID || named(route.result(), "java.lang.Void")
                        ? "void" : tsType(route.result(), interfaces, enums, controller.type());
                if (result == null) return;
                StringBuilder path = new StringBuilder();
                Matcher placeholder = PARAMETER.matcher(route.pattern());
                int at = 0;
                while (placeholder.find()) {
                    path.append(route.pattern(), at, placeholder.start());
                    String javaName = visible.stream().filter(p -> p.kind() == Kind.PATH && p.binding().equals(placeholder.group(1)))
                            .findFirst().orElseThrow().javaName();
                    path.append("${p(").append(tsIdentifier(javaName)).append(")}");
                    at = placeholder.end();
                }
                path.append(route.pattern().substring(at));
                List<String> options = new ArrayList<>();
                if (!query.isEmpty()) options.add("query: { " + String.join(", ", query) + " }");
                if (body != null) options.add(body.equals("body") ? "body" : "body: " + body);
                clients.append("  /** ").append(route.httpMethod()).append(" ").append(route.pattern()).append(" */\n")
                        .append("  ").append(route.method().getSimpleName()).append("(").append(String.join(", ", signature)).append("): Promise<").append(result).append("> {\n")
                        .append("    return request<").append(result).append(">(\"").append(route.httpMethod()).append("\", `").append(path).append("`")
                        .append(options.isEmpty() ? "" : ", { " + String.join(", ", options) + " }").append(");\n")
                        .append("  },\n");
            }
            clients.append("};\n");
        }

        StringBuilder ts = new StringBuilder();
        ts.append("// GENERATED by the webview-4j annotation processor. Do not edit.\n")
                .append("import { request } from \"@webview4j/api\";\n\n")
                .append("const p = (value: unknown): string => encodeURIComponent(String(value));\n");
        for (Map.Entry<String, TypeElement> entry : enums.entrySet()) {
            List<String> constants = new ArrayList<>();
            for (Element constant : entry.getValue().getEnclosedElements()) {
                if (constant.getKind() == ElementKind.ENUM_CONSTANT) constants.add(jsString(constant.getSimpleName().toString()));
            }
            ts.append("\n/** ").append(entry.getValue().getQualifiedName()).append(" */\n")
                    .append("export type ").append(entry.getKey()).append(" = ").append(constants.isEmpty() ? "never" : String.join(" | ", constants)).append(";\n");
        }
        for (Map.Entry<String, TypeElement> entry : new TreeMap<>(interfaces).entrySet()) {
            ts.append("\n/** ").append(entry.getValue().getQualifiedName()).append(" */\n")
                    .append("export interface ").append(entry.getKey()).append(" {\n");
            for (RecordComponentElement component : entry.getValue().getRecordComponents()) {
                String type = tsType(component.asType(), interfaces, enums, entry.getValue());
                if (type == null) return;
                ts.append("  ").append(component.getSimpleName()).append(": ").append(type).append(";\n");
            }
            ts.append("}\n");
        }
        ts.append(clients);

        String target = processingEnv.getOptions().get(TYPESCRIPT_OPTION);
        try {
            if (target != null && !target.isBlank()) {
                Path file = Path.of(target);
                if (file.getParent() != null) Files.createDirectories(file.getParent());
                Files.writeString(file, ts, StandardCharsets.UTF_8);
            } else {
                FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/webview4j/api.ts");
                try (Writer writer = resource.openWriter()) {
                    writer.write(ts.toString());
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "could not write the TypeScript client: " + e.getMessage());
        }
    }

    /** The TypeScript type for {@code type}; records and enums are collected to be declared. */
    private String tsType(TypeMirror type, Map<String, TypeElement> interfaces, Map<String, TypeElement> enums, Element at) {
        switch (type.getKind()) {
            case LONG, INT, DOUBLE: return "number";
            case BOOLEAN: return "boolean";
            default: break;
        }
        if (named(type, "java.lang.String")) return "string";
        if (named(type, "java.lang.Long") || named(type, "java.lang.Integer") || named(type, "java.lang.Double")) return "number";
        if (named(type, "java.lang.Boolean")) return "boolean";
        if (named(type, "java.lang.Object")) return "unknown";
        if (named(erasure(type), "java.util.List")) {
            String element = tsType(typeArgument(type, "java.util.List"), interfaces, enums, at);
            return element == null ? null : (element.contains(" ") ? "(" + element + ")[]" : element + "[]");
        }
        if (named(erasure(type), "java.util.Optional")) {
            String element = tsType(typeArgument(type, "java.util.Optional"), interfaces, enums, at);
            return element == null ? null : element + " | null";
        }
        if (named(erasure(type), "java.util.Map")) {
            String element = tsType(((DeclaredType) type).getTypeArguments().get(1), interfaces, enums, at);
            return element == null ? null : "Record<string, " + element + ">";
        }
        TypeElement declared = (TypeElement) ((DeclaredType) type).asElement();
        String name = declared.getSimpleName().toString();
        Map<String, TypeElement> into = isEnum(type) ? enums : isRecord(type) ? interfaces : null;
        if (into == null) {
            error(at, type + " has no TypeScript form");
            return null;
        }
        TypeElement other = (isEnum(type) ? interfaces : enums).get(name);
        TypeElement known = into.get(name);
        if ((known != null && !known.equals(declared)) || other != null) {
            error(at, "two types are named " + name + " in TypeScript (" + declared.getQualifiedName() + " and "
                    + (known != null ? known : other).getQualifiedName() + "): rename one");
            return null;
        }
        if (known == null) {
            into.put(name, declared);
            if (isRecord(type)) {
                for (RecordComponentElement component : declared.getRecordComponents()) {
                    if (tsType(component.asType(), interfaces, enums, at) == null) return null;
                }
            }
        }
        return name;
    }

    private static String tsIdentifier(String name) {
        return TS_RESERVED.contains(name) ? name + "_" : name;
    }

    private static String jsString(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ---- annotations, read by name ----

    private static AnnotationMirror annotation(Element element, String simpleName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (type.getQualifiedName().contentEquals(ANNOTATIONS + simpleName)) return mirror;
        }
        return null;
    }

    private String string(AnnotationMirror mirror, String key) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals(key)) return String.valueOf(entry.getValue().getValue());
        }
        return "";
    }

    private <T> T error(Element element, String message) {
        failed = true;
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
        return null;
    }
}
