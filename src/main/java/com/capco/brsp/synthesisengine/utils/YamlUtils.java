package com.capco.brsp.synthesisengine.utils;

import com.capco.brsp.synthesisengine.exception.ParsingFileException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.capco.brsp.synthesisengine.utils.JsonUtils.throwableAsJson;

@Slf4j
public class YamlUtils {
    private static final YamlUtils INSTANCE = new YamlUtils();

    private static final ObjectMapper YAML_MAPPER;
    private static final YAMLFactory YAML_FACTORY = new YAMLFactory();
    private static final JavaType MAP_STRING_OBJ_TYPE_REF;

    static {
        YAML_MAPPER = new ObjectMapper(YAML_FACTORY);
        MAP_STRING_OBJ_TYPE_REF = YAML_MAPPER.getTypeFactory().constructMapType(ConcurrentLinkedHashMap.class, String.class, Object.class);
    }

    private YamlUtils() {
    }

    public static YamlUtils getInstance() {
        return INSTANCE;
    }

    public static boolean isValidYaml(Object input) {
        try {
            if (input instanceof String inputString) {
                var obj = YAML_MAPPER.readValue(inputString, Object.class);
                return (obj instanceof Map<?, ?> || obj instanceof List<?>);
            }
        } catch (JsonProcessingException ignore) {
        }

        return false;
    }

    public static Object readYAML(String content) throws JsonProcessingException {
        if (content == null) {
            return null;
        }

        return YAML_MAPPER.readValue(content, Object.class);
    }

    public static Map<String, Object> readYAMLFile(String filePath) throws IOException {
        File yamlFile = new File(filePath);

        return YAML_MAPPER.readValue(yamlFile, MAP_STRING_OBJ_TYPE_REF);
    }

    public static Map<String, Object> readYAMLContent(String content) throws JsonProcessingException {
        if (content == null || content.isBlank()) {
            return new ConcurrentLinkedHashMap<>();
        }

        return new ConcurrentLinkedHashMap<>(YAML_MAPPER.readValue(content, MAP_STRING_OBJ_TYPE_REF));
    }

    public static <T> Map<String, T> readYAMLContentAsMap(String inputContent, Class<T> clazz) {
        JavaType valueType = YAML_MAPPER.getTypeFactory().constructMapType(ConcurrentLinkedHashMap.class, String.class, clazz);
        try {
            return YAML_MAPPER.readValue(inputContent, valueType);
        } catch (Exception e) {
            throw new ParsingFileException(inputContent, valueType, e);
        }
    }

    public static <T> List<T> readYAMLContentAsList(String inputContent, Class<T> clazz) {
        JavaType valueType = YAML_MAPPER.getTypeFactory().constructCollectionType(ConcurrentLinkedList.class, clazz);
        try {
            return YAML_MAPPER.readValue(inputContent, valueType);
        } catch (Exception e) {
            throw new ParsingFileException(inputContent, valueType, e);
        }
    }

    public static String writeAsString(Object value) {
        try {
            return YAML_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to json string!", e);
            return throwableAsJson(e);
        }
    }

    public static void traverse(Map<String, Object> output, Object obj, String path, Function<Object, Object> keyTransform) {
        switch (obj) {
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    var newKey = keyTransform == null ? entry.getKey().toString() : keyTransform.apply(entry.getKey());
                    if (newKey instanceof String newKeyString) {
                        String currentPath = path.isEmpty() ? newKeyString : path + File.separator + newKeyString;

                        // TODO: Review
                        if (newKeyString.startsWith("$$")) {
                            output.put(currentPath, entry.getValue());
                            continue;
                        }

                        traverse(output, entry.getValue(), currentPath, keyTransform);
                    } else if (newKey instanceof List<?> newKeyList) {
                        var newMap = new ConcurrentLinkedHashMap<>();
                        for (var newKeyListItem : newKeyList) {
                            if (newKeyListItem instanceof String newKeyListItemString) {
                                newMap.put(newKeyListItemString, entry.getValue());
                            } else {
                                throw new IllegalArgumentException("Not sure on how to traverse over this object!");
                            }
                        }
                        traverse(output, newMap, path, keyTransform);
                    }
                }
            }
            case List<?> list -> {
                for (Object o : list) {
                    var newObject = keyTransform == null ? o : keyTransform.apply(o);
                    String currentPath = path + File.separator;
                    traverse(output, newObject, currentPath, keyTransform);
                }
            }
            case null, default -> output.put(path, obj);
        }
    }
}
