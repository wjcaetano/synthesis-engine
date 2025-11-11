package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.exception.ParsingFileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.*;
import com.jayway.jsonpath.JsonPath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.proleap.cobol.asg.metamodel.ModelElement;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.tree.Tree;

import java.io.InputStream;
import java.util.*;

@Slf4j
public class JsonUtils {
    private static final JsonUtils INSTANCE = new JsonUtils();
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectWriter JSON_PRETTY_WRITER = JSON_OBJECT_MAPPER.writer(new JsonCustomPrettyPrinter());
    private static final JsonSchemaFactory SCHEMA_FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private JsonUtils() {
    }

    public static JsonUtils getInstance() {
        return INSTANCE;
    }

    public static Object fromPath(Object reference, String path) {
        return JsonPath.read(reference, path);
    }

    public static boolean isValidJson(Object input) {
        try {
            if (input instanceof String inputString) {
                var obj = JSON_OBJECT_MAPPER.readValue(inputString, Object.class);
                return (obj instanceof Map<?, ?> || obj instanceof List<?>);
            }
        } catch (JsonProcessingException ignore) {
        }

        return false;
    }

    public static <T> T readAs(String value, Class<T> dtoClass) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(value, dtoClass);
    }

    public static <T> List<T> readAsListOf(String value, Class<T> dtoClass) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(value, objectMapper.getTypeFactory().constructCollectionType(ConcurrentLinkedList.class, dtoClass));
    }

    public static Object readAsObject(String value, Object defaultValue) throws JsonProcessingException {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Object parsedObject = objectMapper.readValue(value, Object.class);
        if (parsedObject == null) {
            parsedObject = defaultValue;
        }

        if (parsedObject instanceof List<?> parsedList) {
            return new ConcurrentLinkedList<>(parsedList);
        } else if (parsedObject instanceof Map<?, ?> parsedMap) {
            return new ConcurrentLinkedHashMap<>(parsedMap);
        }

        return parsedObject;
    }

    public static List<Object> readAsList(String value) throws JsonProcessingException {
        if (value == null || value.isBlank()) {
            return new ConcurrentLinkedList<>();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        return new ConcurrentLinkedList<Object>(objectMapper.readValue(value, ConcurrentLinkedList.class));
    }

    public static Map<String, Object> readAsMap(String value) throws JsonProcessingException {
        if (value == null || value.isBlank()) {
            return new ConcurrentLinkedHashMap<>();
        }

        ObjectMapper objectMapper = new ObjectMapper();
        return new ConcurrentLinkedHashMap<String, Object>(objectMapper.readValue(value, ConcurrentLinkedHashMap.class));
    }

    public static <T> Map<String, T> readAsMap(String inputContent, Class<T> clazz) {
        JavaType valueType = JSON_OBJECT_MAPPER.getTypeFactory().constructMapType(ConcurrentLinkedHashMap.class, String.class, clazz);
        try {
            return JSON_OBJECT_MAPPER.readValue(inputContent, valueType);
        } catch (Exception e) {
            throw new ParsingFileException(inputContent, valueType, e);
        }
    }

    public static <T> List<T> parseInputStreamToList(InputStream inputStream, Class<T> clazz) {
        JavaType type = JSON_OBJECT_MAPPER.getTypeFactory().constructCollectionType(ConcurrentLinkedList.class, clazz);
        try {
            return JSON_OBJECT_MAPPER.readValue(inputStream, type);
        } catch (Exception e) {
            throw new ParsingFileException(inputStream, type, e);
        }
    }

    public static <T> T convert(Object value, Class<T> clzz) {
        return JSON_OBJECT_MAPPER.convertValue(value, clzz);
    }

    public static <T> Map<String, T> convertAsMapOf(Object value, Class<T> clzz) {
        JavaType valueType = JSON_OBJECT_MAPPER.getTypeFactory().constructMapType(ConcurrentLinkedHashMap.class, String.class, clzz);
        try {
            return JSON_OBJECT_MAPPER.convertValue(value, valueType);
        } catch (Exception e) {
            throw new ParsingFileException(value, valueType, e);
        }
    }

    public static <T> T convertAsListOf(Object value, Class<T> clzz) {
        JavaType valueType = JSON_OBJECT_MAPPER.getTypeFactory().constructCollectionType(ConcurrentLinkedList.class, clzz);
        try {
            return JSON_OBJECT_MAPPER.convertValue(value, valueType);
        } catch (Exception e) {
            throw new ParsingFileException(value, valueType, e);
        }
    }

    public static String throwableAsJson(Throwable throwable) {
        try {
            Map<String, Object> throwableJson = new ConcurrentLinkedHashMap<>();
            throwableJson.put("error", true);
            throwableJson.put("message", throwable.getMessage());
            throwableJson.put("causeMessage", Utils.nullSafeInvoke(throwable.getCause(), Throwable::getMessage));

            return JSON_PRETTY_WRITER.writeValueAsString(throwableJson);
        } catch (JsonProcessingException e) {
            log.error("Failed to create json from throwable message/causeMessage!", e);
            return "JsonUtils to create json from throwable message/causeMessage!\n\n" + e.getMessage();
        }
    }

    public static String writeAsJsonStringCircular(Object value, boolean isPretty, boolean unsafe) {
        var snapshot = Utils.convertToConcurrent(value);
        Utils.replaceKeysWithMockValue(snapshot, "[reference omitted due to circular structure]", "parent", "meta", ModelElement.class, Tree.class);
        return writeAsJsonString(snapshot, isPretty, false);
    }

    public static String writeAsJsonString(Object value, boolean isPretty) {
        return writeAsJsonString(value, isPretty, false);
    }


    public static String writeAsJsonString(Object value, boolean isPretty, boolean unsafe) {
        try {
            return isPretty ? JSON_PRETTY_WRITER.writeValueAsString(value) : JSON_OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            if (!unsafe) {
                log.error("Failed to serialize object to json string!", e);
                return throwableAsJson(e);
            }
            throw new RuntimeException(e);
        }
    }

    public static List<String> getErrorsAgainstJsonSchema(@NonNull Object content, @NonNull String jsonSchema) throws JsonProcessingException {
        if (jsonSchema.isBlank()) {
            throw new IllegalArgumentException("JSON schema cannot be null or blank");
        }

        String jsonString = content instanceof String
                ? (String) content
                : JsonUtils.writeAsJsonString(content, false);

        JsonNode schemaNode = JSON_OBJECT_MAPPER.readTree(jsonSchema);
        JsonNode dataNode = JSON_OBJECT_MAPPER.readTree(jsonString);

        JsonSchema schema = SCHEMA_FACTORY.getSchema(schemaNode);

        return schema.validate(dataNode).stream().map(ValidationMessage::getMessage).toList();
    }

    public static JsonNode generateSample(String schemaText) throws JsonProcessingException {
        JsonNode schema = JSON_OBJECT_MAPPER.readTree(schemaText);
        return generateFromSchema(schema, new IdentityHashMap<>());
    }

    private static JsonNode generateFromSchema(JsonNode schema, Map<JsonNode, Boolean> seen) {
        if (seen.putIfAbsent(schema, true) != null) {
            return NullNode.instance;
        }
        if (schema.has("default")) {
            return schema.get("default");
        }
        if (schema.has("const")) {
            return schema.get("const");
        }
        if (schema.has("enum") && schema.get("enum").isArray()) {
            JsonNode first = schema.get("enum").get(0);
            if (first != null) return first;
        }
        if (schema.has("oneOf")) {
            return generateFromSchema(schema.get("oneOf").get(0), seen);
        }
        if (schema.has("anyOf")) {
            return generateFromSchema(schema.get("anyOf").get(0), seen);
        }

        if (schema.has("allOf") && schema.get("allOf").isArray()) {
            ObjectNode merged = JSON_OBJECT_MAPPER.createObjectNode();
            for (JsonNode subschema : schema.get("allOf")) {
                JsonNode sample = generateFromSchema(subschema, seen);
                if (sample.isObject()) {
                    merged.setAll((ObjectNode) sample);
                }
            }
            return merged;
        }

        JsonNode typeNode = schema.get("type");
        String type = Optional.ofNullable(typeNode)
                .map(n -> n.isTextual() ? n.asText() : n.get(0).asText())
                .orElse("object");

        switch (type) {
            case "object":
                ObjectNode obj = JSON_OBJECT_MAPPER.createObjectNode();
                JsonNode props = schema.path("properties");

                Iterator<String> names = props.fieldNames();
                names.forEachRemaining(name -> {
                    obj.set(name, generateFromSchema(props.get(name), seen));
                });
                return obj;

            case "array":
                ArrayNode arr = JSON_OBJECT_MAPPER.createArrayNode();
                JsonNode items = schema.get("items");
                if (items != null) {
                    arr.add(generateFromSchema(items, seen));
                }
                return arr;

            case "string":
                String fmt = schema.path("format").asText(null);
                if ("date".equals(fmt)) return new TextNode("1970-01-01");
                if ("date-time".equals(fmt)) return new TextNode("1970-01-01T00:00:00Z");
                if ("email".equals(fmt)) return new TextNode("user@example.com");
                return new TextNode("string");

            case "integer":
                if (schema.has("minimum")) {
                    return new IntNode(schema.get("minimum").asInt());
                }
                return new IntNode(1);

            case "number":
                if (schema.has("minimum")) {
                    return new DoubleNode(schema.get("minimum").asDouble());
                }
                return new DoubleNode(1.0);

            case "boolean":
                return BooleanNode.TRUE;

            case "null":
                return NullNode.instance;

            default:
                return JSON_OBJECT_MAPPER.createObjectNode();
        }
    }
}