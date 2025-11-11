package com.capco.brsp.synthesisengine.tools;

import com.capco.brsp.synthesisengine.utils.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class Transforms {

    private static final List<String> SKIP_LIST = List.of("key", "labels", "meta", "parent", "parentRelationship", "relationships");

    public static Object nodify(Object input) throws JsonProcessingException {
        if (input instanceof String inputString) {
            if (JsonUtils.isValidJson(inputString)) {
                input = JsonUtils.readAsObject(inputString, Object.class);
            } else if (YamlUtils.isValidYaml(inputString)) {
                input = YamlUtils.readYAML(inputString);
            } else if (XmlUtils.isValidXml(inputString)) {
                input = XmlUtils.readAs(inputString, Object.class);
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> relationships = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        if (input instanceof Collection<?> inputCollection) {
            for (var inputItem : inputCollection) {
                processEntity(inputItem, nodes, relationships, null, seenKeys);
            }
        } else {
            processEntity(input, nodes, relationships, null, seenKeys);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("relationships", relationships);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static void processEntity(Object obj, List<Map<String, Object>> nodes, List<Map<String, Object>> relationships, String parentKey, Set<String> seenKeys) {
        if (!(obj instanceof Map<?, ?> rawMap)) return;

        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((k, v) -> map.put(String.valueOf(k), v));

        if (map.containsKey("key") && map.containsKey("labels")) {
            String currentKey = String.valueOf(map.get("key"));

            if (seenKeys.add(currentKey)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("key", currentKey);
                node.put("labels", map.get("labels"));

                if (map.get("relationships") instanceof Collection<?> hardDefinedRelationships) {
                    for (var hardDefinedRelationship : hardDefinedRelationships) {
                        String startKey = null, endKey = null, label = null;
                        if (hardDefinedRelationship instanceof String hardDefinedRelationshipString) {
                            label = Utils.getRegexGroup(hardDefinedRelationshipString, "(\\[(\\w+)\\])?.*$", 2);
                            endKey = Utils.nvl(Utils.getRegexGroup(hardDefinedRelationshipString, "(\\[\\w+\\])?(.*)$", 2), "CONTAINS");
                        } else if (hardDefinedRelationship instanceof Map<?, ?> hardDefinedRelationshipMap) {
                            label = Utils.castOrDefault(hardDefinedRelationshipMap.get("label"), String.class, null);
                            startKey = Utils.castOrDefault(hardDefinedRelationshipMap.get("startKey"), String.class, null);
                            endKey = Utils.castOrDefault(hardDefinedRelationshipMap.get("endKey"), String.class, null);
                        }

                        startKey = Utils.nvl(startKey, currentKey);

                        createRelationship(relationships, startKey, endKey, label);
                    }
                }

                node.put("parentRelationship", map.get("parentRelationship"));

                // Extract filtered properties
                Map<String, Object> properties = new LinkedHashMap<>();

                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    String field = entry.getKey();
                    Object value = entry.getValue();

                    // Skip reserved keys
                    if (SKIP_LIST.contains(field) || field.startsWith("_")) continue;

                    // Check if value is a node or a list of nodes
                    if (value instanceof Map<?, ?> valueMap && valueMap.containsKey("labels")) {
                        // It's a child node → create relationship
                        String targetKey = String.valueOf(valueMap.get("key"));
                        String parentRelationship = String.valueOf(valueMap.get("parentRelationship"));
                        createRelationship(relationships, currentKey, targetKey, parentRelationship);
                        processEntity(valueMap, nodes, relationships, currentKey, seenKeys);
                    } else if (value instanceof List<?> list && allMapsWithLabels(list)) {
                        for (Object item : list) {
                            Map<?, ?> itemMap = (Map<?, ?>) item;
                            String targetKey = String.valueOf(itemMap.get("key"));
                            String parentRelationship = Utils.nvl((String) itemMap.get("parentRelationship"), "CONTAINS");
                            createRelationship(relationships, currentKey, targetKey, parentRelationship);
                            processEntity(itemMap, nodes, relationships, currentKey, seenKeys);
                        }
                    } else {
                        // It's a property
                        properties.put(field, value);

                        // Also check if it contains more entities
                        processEntity(value, nodes, relationships, currentKey, seenKeys);
                    }
                }

                node.put("properties", properties);
                nodes.add(node);
            }
        } else if (!map.isEmpty()) {
            throw new IllegalArgumentException("Invalid entity structure: missing 'key' or 'labels'. Entity:\n" + JsonUtils.writeAsJsonStringCircular(map, true, false));
        }
    }

    private static boolean allMapsWithLabels(List<?> list) {
        if (list.isEmpty()) return false;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map) || !map.containsKey("labels")) {
                return false;
            }
        }
        return true;
    }

    private static void createRelationship(List<Map<String, Object>> relationships, String startKey, String endKey, String name) {
        if (!Objects.equals(startKey, endKey)) {
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("startKey", startKey);
            rel.put("endKey", endKey);
            rel.put("label", name);
            relationships.add(rel);
        }
    }
}
