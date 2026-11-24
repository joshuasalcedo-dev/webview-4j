package io.joshuasalcedo.webview4j.spring.client;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A call's arguments checked with Jakarta Validation before it is sent: the method's parameter constraints
 * ({@code @NotNull}, {@code @Pattern}...) and, through {@code @Valid}, its records' ({@code @Email}, {@code @Size}...),
 * the same rules the server applies. Only loaded when Jakarta Validation is on the class path.
 */
final class JakartaChecker implements ClientScreen.Checker {

    private final Validator validator;

    JakartaChecker(Validator validator) {
        this.validator = validator;
    }

    @Override
    public Map<String, String> check(Object client, Method method, Object[] args, BiFunction<Parameter, Integer, String> names) {
        Map<String, String> wrong = new LinkedHashMap<>();
        Set<ConstraintViolation<Object>> violations;
        try {
            violations = validator.forExecutables().validateParameters(client, method, args);
        } catch (RuntimeException e) {
            // A client proxy the validator cannot read: check each record argument as a bean instead.
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null || !args[i].getClass().isRecord()) continue;
                String name = names.apply(parameters[i], i);
                for (ConstraintViolation<Object> v : validator.validate(args[i])) {
                    wrong.putIfAbsent(name + "." + v.getPropertyPath(), v.getMessage());
                }
            }
            return wrong;
        }
        Parameter[] parameters = method.getParameters();
        for (ConstraintViolation<Object> v : violations) {
            StringBuilder key = new StringBuilder();
            for (Path.Node node : v.getPropertyPath()) {
                if (node.getKind() == ElementKind.PARAMETER) {
                    int index = node.as(Path.ParameterNode.class).getParameterIndex();
                    key.append(names.apply(parameters[index], index));
                } else if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                    key.append('.').append(node.getName());
                }
            }
            wrong.putIfAbsent(key.toString(), v.getMessage());
        }
        return wrong;
    }
}
