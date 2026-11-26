package io.joshuasalcedo.webview4j.explorer;

import io.joshuasalcedo.webview4j.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every public type and member of core, as the explorer lists them: made at build time from pretty-docs' model
 * ({@code docs.json}) by {@link #main}, kept to the packages core's module exports, and read by the app from
 * {@code explorer/api-index.json} on its class path.
 */
public record ApiIndex(String title, String generatedAt, List<Type> types) {

    /** Where the build puts the index, on the class path. */
    public static final String RESOURCE = "explorer/api-index.json";

    /** A parameter: its name and its type as written ({@code Consumer<String>}), and the type's qualified name. */
    public record Param(String name, String type, String qualifiedType, boolean varArgs) {}

    /** A javadoc block tag: {@code @param name text}, {@code @return text}... */
    public record Tag(String name, String param, String text) {}

    /** What a member is. */
    public enum Kind { CONSTRUCTOR, CONSTANT, FIELD, METHOD }

    /** A constructor, enum constant, field or method. */
    public record Member(Kind kind, String name, String signature, String returnType, String returnQualified,
                         boolean isStatic, List<Param> params, String doc, List<Tag> tags) {

        /** {@code subtle()}, {@code value(String)}: the name and the parameters' types. */
        public String shortSignature() {
            if (kind == Kind.CONSTANT || kind == Kind.FIELD) return name;
            List<String> types = params.stream().map(p -> p.varArgs() ? p.type().replaceAll("\\[]$", "") + "..." : p.type()).toList();
            return name + "(" + String.join(", ", types) + ")";
        }
    }

    /** A class, interface, enum, record or annotation, with its members. */
    public record Type(String qualifiedName, String name, String packageName, String kind, String signature,
                       String superclass, List<String> interfaces, String doc, List<Tag> tags, List<Member> members) {}

    /** The class every component extends. */
    public static final String COMPONENT = "io.joshuasalcedo.webview4j.ui.Component";

    /** Whether {@code qualifiedName} is a component: {@link #COMPONENT} or a class that extends it. */
    public boolean isComponent(String qualifiedName) {
        Map<String, Type> byName = new java.util.HashMap<>();
        for (Type t : types) byName.put(t.qualifiedName(), t);
        for (int depth = 0; qualifiedName != null && depth < 20; depth++) {
            if (qualifiedName.equals(COMPONENT)) return true;
            Type t = byName.get(qualifiedName);
            if (t == null) return false;
            qualifiedName = t.superclass();
        }
        return false;
    }

    // ---- the build: docs.json -> api-index.json ----

    /** {@code ApiIndex <docs.json> <module-info.java> <api-index.json>}: run by the explorer's apiIndex task. */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException("usage: ApiIndex <docs.json> <module-info.java> <api-index.json>");
        Set<String> exported = exports(Files.readString(Path.of(args[1])));
        ApiIndex index = fromPrettyDocs(Json.parse(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)), exported);
        Path out = Path.of(args[2]);
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.write(index.toJson()), StandardCharsets.UTF_8);
        long members = index.types.stream().mapToLong(t -> t.members().size()).sum();
        System.out.println("api-index: " + index.types.size() + " types, " + members + " members, from "
                + exported.size() + " exported packages -> " + out);
    }

    /** The packages a module-info.java exports to everyone. */
    static Set<String> exports(String moduleInfo) {
        Set<String> packages = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^\\s*exports\\s+([\\w.]+)\\s*;").matcher(moduleInfo.replaceAll("(?s)/\\*.*?\\*/", ""));
        while (m.find()) packages.add(m.group(1));
        return packages;
    }

    /** The index of pretty-docs' model {@code site} (docs.json, parsed), with the types of {@code packages} only. */
    static ApiIndex fromPrettyDocs(Object site, Set<String> packages) {
        Map<?, ?> root = map(site);
        List<Type> types = new ArrayList<>();
        for (Object module : list(root.get("modules"))) {
            for (Object file : list(map(module).get("files"))) {
                String pkg = str(map(file).get("packageName"));
                if (!packages.contains(pkg)) continue;
                for (Object type : list(map(file).get("types"))) collect(map(type), pkg, false, types);
            }
        }
        types.sort(Comparator.comparing(Type::packageName).thenComparing(Type::qualifiedName));
        return new ApiIndex(str(root.get("title")), str(root.get("generatedAt")), types);
    }

    private static void collect(Map<?, ?> type, String pkg, boolean inInterface, List<Type> into) {
        Set<String> modifiers = modifiers(type);
        String kind = str(type.get("kind"));
        boolean visible = modifiers.contains("public") || (inInterface && !modifiers.contains("private"));
        if (!visible) return;
        boolean iface = kind.equals("INTERFACE") || kind.equals("ANNOTATION");
        String qualified = str(type.get("qualifiedName"));
        String simple = qualified.startsWith(pkg + ".") ? qualified.substring(pkg.length() + 1) : str(type.get("name"));

        List<Member> members = new ArrayList<>();
        for (Object c : list(type.get("constructors"))) {
            Map<?, ?> ctor = map(c);
            if (!memberVisible(ctor, iface)) continue;
            List<Param> params = params(ctor);
            members.add(new Member(Kind.CONSTRUCTOR, str(type.get("name")),
                    join(mods(ctor, false), typeParams(ctor) + str(type.get("name")) + paramList(params) + throwsList(ctor)),
                    simple, qualified, false, params, doc(ctor), tags(ctor)));
        }
        for (Object e : list(type.get("enumConstants"))) {
            Map<?, ?> constant = map(e);
            members.add(new Member(Kind.CONSTANT, str(constant.get("name")), str(constant.get("name")), simple, qualified,
                    true, List.of(), doc(constant), tags(constant)));
        }
        for (Object f : list(type.get("fields"))) {
            Map<?, ?> field = map(f);
            if (!memberVisible(field, iface)) continue;
            Map<?, ?> t = map(field.get("type"));
            boolean isStatic = iface || modifiers(field).contains("static");
            members.add(new Member(Kind.FIELD, str(field.get("name")),
                    join(mods(field, false), type(t) + " " + str(field.get("name"))),
                    type(t), qualifiedOf(t), isStatic, List.of(), doc(field), tags(field)));
        }
        for (Object m : list(type.get("methods"))) {
            Map<?, ?> method = map(m);
            if (!memberVisible(method, iface)) continue;
            List<Param> params = params(method);
            Map<?, ?> returns = map(method.get("returnType"));
            String returnType = returns.isEmpty() ? "void" : type(returns);
            members.add(new Member(Kind.METHOD, str(method.get("name")),
                    join(mods(method, false), typeParams(method) + returnType + " " + str(method.get("name")) + paramList(params) + throwsList(method)),
                    returnType, returns.isEmpty() ? "void" : qualifiedOf(returns), modifiers(method).contains("static"),
                    params, doc(method), tags(method)));
        }
        Map<?, ?> superclass = map(type.get("superclass"));
        List<String> interfaces = list(type.get("interfaces")).stream().map(i -> {
            Map<?, ?> ref = map(i);
            return ref.get("qualifiedName") != null ? str(ref.get("qualifiedName")) : str(ref.get("name"));
        }).toList();
        StringBuilder signature = new StringBuilder(join(mods(type, true), keyword(kind) + " " + simple + typeParams(type)));
        if (!superclass.isEmpty() && !kind.equals("ENUM") && !kind.equals("RECORD")) signature.append(" extends ").append(str(superclass.get("name")));
        if (!interfaces.isEmpty()) {
            signature.append(iface ? " extends " : " implements ")
                    .append(String.join(", ", list(type.get("interfaces")).stream().map(i -> str(map(i).get("name"))).toList()));
        }
        into.add(new Type(qualified, simple, pkg, kind, signature.toString(),
                superclass.isEmpty() ? null : superclass.get("qualifiedName") != null ? str(superclass.get("qualifiedName")) : str(superclass.get("name")),
                interfaces, doc(type), tags(type), members));
        for (Object nested : list(type.get("nestedTypes"))) collect(map(nested), pkg, iface, into);
    }

    private static boolean memberVisible(Map<?, ?> member, boolean inInterface) {
        Set<String> m = modifiers(member);
        return m.contains("public") || m.contains("protected") || (inInterface && !m.contains("private"));
    }

    private static Set<String> modifiers(Map<?, ?> element) {
        Set<String> out = new LinkedHashSet<>();
        for (Object m : list(element.get("modifiers"))) {
            String kind = str(map(m).get("kind"));
            switch (kind) {
                case "PACKAGE_PRIVATE" -> {}
                case "NON_SEALED" -> out.add("non-sealed");
                default -> out.add(kind.toLowerCase());
            }
        }
        return out;
    }

    private static String mods(Map<?, ?> element, boolean type) {
        Set<String> m = modifiers(element);
        if (type) m.remove("static");
        return String.join(" ", m);
    }

    private static String keyword(String kind) {
        return switch (kind) {
            case "INTERFACE" -> "interface";
            case "ENUM" -> "enum";
            case "RECORD" -> "record";
            case "ANNOTATION" -> "@interface";
            default -> "class";
        };
    }

    private static List<Param> params(Map<?, ?> executable) {
        List<Param> out = new ArrayList<>();
        for (Object p : list(executable.get("parameters"))) {
            Map<?, ?> param = map(p);
            Map<?, ?> t = map(param.get("type"));
            out.add(new Param(str(param.get("name")), type(t), qualifiedOf(t), Boolean.TRUE.equals(param.get("isVarArgs"))));
        }
        return out;
    }

    private static String paramList(List<Param> params) {
        List<String> out = new ArrayList<>();
        for (Param p : params) {
            String t = p.varArgs() ? p.type().replaceAll("\\[]$", "") + "..." : p.type();
            out.add(t + " " + p.name());
        }
        return "(" + String.join(", ", out) + ")";
    }

    private static String throwsList(Map<?, ?> executable) {
        List<String> thrown = list(executable.get("thrownExceptions")).stream().map(t -> type(map(t))).toList();
        return thrown.isEmpty() ? "" : " throws " + String.join(", ", thrown);
    }

    private static String typeParams(Map<?, ?> element) {
        List<String> out = new ArrayList<>();
        for (Object p : list(element.get("typeParameters"))) {
            Map<?, ?> param = map(p);
            List<String> bounds = list(param.get("bounds")).stream().map(b -> type(map(b))).toList();
            out.add(str(param.get("name")) + (bounds.isEmpty() ? "" : " extends " + String.join(" & ", bounds)));
        }
        return out.isEmpty() ? "" : "<" + String.join(", ", out) + "> ";
    }

    /** A type reference as written: {@code Map<String, List<Integer>>[]}. */
    private static String type(Map<?, ?> ref) {
        if (ref.isEmpty()) return "?";
        if (Boolean.TRUE.equals(ref.get("isWildcard"))) {
            Map<?, ?> bound = map(ref.get("wildcardBound"));
            String kind = str(ref.get("wildcardKind"));
            if (bound.isEmpty() || kind.equals("UNBOUNDED")) return "?";
            return "? " + (kind.equals("SUPER") ? "super " : "extends ") + type(bound);
        }
        StringBuilder out = new StringBuilder(str(ref.get("name")));
        List<Object> args = list(ref.get("typeArguments"));
        if (!args.isEmpty() && out.indexOf("<") < 0) {
            out.append('<').append(String.join(", ", args.stream().map(a -> type(map(a))).toList())).append('>');
        }
        long dims = ref.get("arrayDimensions") instanceof Number n ? n.longValue() : 0;
        if (out.indexOf("[") < 0) out.append("[]".repeat((int) dims));
        return out.toString();
    }

    private static String qualifiedOf(Map<?, ?> ref) {
        Object q = ref.get("qualifiedName");
        String name = q != null ? str(q) : str(ref.get("name"));
        int generic = name.indexOf('<');
        return generic < 0 ? name : name.substring(0, generic);
    }

    private static String doc(Map<?, ?> element) {
        return str(map(element.get("javadoc")).get("description")).replace("\r\n", "\n").strip();
    }

    private static List<Tag> tags(Map<?, ?> element) {
        List<Tag> out = new ArrayList<>();
        for (Object t : list(map(element.get("javadoc")).get("tags"))) {
            Map<?, ?> tag = map(t);
            out.add(new Tag(str(tag.get("name")), tag.get("paramName") == null ? null : str(tag.get("paramName")),
                    str(tag.get("text")).replace("\r\n", "\n").strip()));
        }
        return out;
    }

    private static String join(String modifiers, String rest) {
        return modifiers.isEmpty() ? rest : modifiers + " " + rest;
    }

    // ---- the app: api-index.json -> ApiIndex ----

    /** The index the build put on the class path, if it is there. */
    public static Optional<ApiIndex> load(ClassLoader loader) {
        try (InputStream in = loader.getResourceAsStream(RESOURCE)) {
            if (in == null) return Optional.empty();
            return Optional.of(fromJson(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Map<String, Object> toJson() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", title);
        out.put("generatedAt", generatedAt);
        List<Object> list = new ArrayList<>();
        for (Type t : types) {
            Map<String, Object> type = new LinkedHashMap<>();
            type.put("qualifiedName", t.qualifiedName());
            type.put("name", t.name());
            type.put("package", t.packageName());
            type.put("kind", t.kind());
            type.put("signature", t.signature());
            type.put("superclass", t.superclass());
            type.put("interfaces", t.interfaces());
            type.put("doc", t.doc());
            type.put("tags", tagsJson(t.tags()));
            List<Object> members = new ArrayList<>();
            for (Member m : t.members()) {
                Map<String, Object> member = new LinkedHashMap<>();
                member.put("kind", m.kind().name());
                member.put("name", m.name());
                member.put("signature", m.signature());
                member.put("returnType", m.returnType());
                member.put("returnQualified", m.returnQualified());
                member.put("static", m.isStatic());
                List<Object> params = new ArrayList<>();
                for (Param p : m.params()) {
                    Map<String, Object> param = new LinkedHashMap<>();
                    param.put("name", p.name());
                    param.put("type", p.type());
                    param.put("qualifiedType", p.qualifiedType());
                    param.put("varArgs", p.varArgs());
                    params.add(param);
                }
                member.put("params", params);
                member.put("doc", m.doc());
                member.put("tags", tagsJson(m.tags()));
                members.add(member);
            }
            type.put("members", members);
            list.add(type);
        }
        out.put("types", list);
        return out;
    }

    private static List<Object> tagsJson(List<Tag> tags) {
        List<Object> out = new ArrayList<>();
        for (Tag tag : tags) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("name", tag.name());
            t.put("param", tag.param());
            t.put("text", tag.text());
            out.add(t);
        }
        return out;
    }

    static ApiIndex fromJson(Object json) {
        Map<?, ?> root = map(json);
        List<Type> types = new ArrayList<>();
        for (Object t : list(root.get("types"))) {
            Map<?, ?> type = map(t);
            List<Member> members = new ArrayList<>();
            for (Object m : list(type.get("members"))) {
                Map<?, ?> member = map(m);
                List<Param> params = list(member.get("params")).stream().map(p -> {
                    Map<?, ?> param = map(p);
                    return new Param(str(param.get("name")), str(param.get("type")), str(param.get("qualifiedType")),
                            Boolean.TRUE.equals(param.get("varArgs")));
                }).toList();
                members.add(new Member(Kind.valueOf(str(member.get("kind"))), str(member.get("name")), str(member.get("signature")),
                        str(member.get("returnType")), str(member.get("returnQualified")), Boolean.TRUE.equals(member.get("static")),
                        params, str(member.get("doc")), tagsOf(member.get("tags"))));
            }
            types.add(new Type(str(type.get("qualifiedName")), str(type.get("name")), str(type.get("package")), str(type.get("kind")),
                    str(type.get("signature")), type.get("superclass") == null ? null : str(type.get("superclass")),
                    list(type.get("interfaces")).stream().map(ApiIndex::str).toList(), str(type.get("doc")),
                    tagsOf(type.get("tags")), members));
        }
        return new ApiIndex(str(root.get("title")), str(root.get("generatedAt")), types);
    }

    private static List<Tag> tagsOf(Object json) {
        return list(json).stream().map(t -> {
            Map<?, ?> tag = map(t);
            return new Tag(str(tag.get("name")), tag.get("param") == null ? null : str(tag.get("param")), str(tag.get("text")));
        }).toList();
    }

    // ---- JSON as parsed: maps, lists, strings ----

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
