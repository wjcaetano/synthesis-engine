package com.capco.brsp.synthesisengine.utils;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public class TypeScriptUtils {

    public static String escapeJsString(String input) {
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    public static String generateVariable(String name, Object value, boolean isConst) {
        String declarationType = isConst ? "const" : "let";
        String tsType = resolveTypeScriptType(value);
        String jsonValue = JsonUtils.writeAsJsonString(value, false);
        return declarationType + " " + name + ": " + tsType + " = " + jsonValue + ";";
    }

    public static String generateFunction(String name, Map<String, Object> argsWithValues, String returnType, String body) {
        StringJoiner args = new StringJoiner(", ");
        for (Map.Entry<String, Object> entry : argsWithValues.entrySet()) {
            String tsType = resolveTypeScriptType(entry.getValue());
            args.add(entry.getKey() + ": " + tsType);
        }
        return "function " + name + "(" + args + "): " + returnType + " {\n" + body + "\n}";
    }

    public static String generateFunctionCall(String functionName, Object... args) {
        StringJoiner sj = new StringJoiner(", ");
        for (Object arg : args) {
            sj.add(JsonUtils.writeAsJsonString(arg, false));
        }
        return functionName + "(" + sj + ");";
    }

    public static String generateTsObject(Map<String, Object> data) {
        return JsonUtils.writeAsJsonString(data, false);
    }

    public static String applyTemplate(String template, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            template = template.replace(
                    "{{" + entry.getKey() + "}}", JsonUtils.writeAsJsonString(entry.getValue(), false)
            );
        }
        return template;
    }

    public static boolean isValidJavaScriptIdentifier(String str) {
        return str.matches("^[a-zA-Z_$][a-zA-Z\\d_$]*$");
    }

    public static String wrapInScriptTag(String tsCode) {
        return "<script type=\"module\">\n" + tsCode + "\n</script>";
    }

    public static String minifyTs(String code) {
        return code.replaceAll("//.*|/\\*(.|\\R)*?\\*/", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*([{}();,:=+\\-*/<>])\\s*", "$1")
                .trim();
    }

    public static String generateCompleteTypeScript(List<String> parts) {
        return String.join("\n", parts);
    }

    public static String resolveTypeScriptType(Object obj) {
        if (obj == null) return "any";
        return resolveTypeScriptType(obj.getClass());
    }

    public static String resolveTypeScriptType(Class<?> clazz) {
        if (clazz == String.class) return "string";
        if (clazz == Integer.class || clazz == Long.class || clazz == Double.class || clazz == Float.class || clazz == Short.class || clazz == Byte.class || clazz == int.class || clazz == double.class || clazz == long.class || clazz == float.class)
            return "number";
        if (clazz == Boolean.class || clazz == boolean.class) return "boolean";
        if (List.class.isAssignableFrom(clazz)) return "any[]";
        if (Map.class.isAssignableFrom(clazz)) return "any";
        if (clazz.isArray()) return resolveTypeScriptType(clazz.getComponentType()) + "[]";
        return "any";
    }

}
