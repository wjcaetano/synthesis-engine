package com.capco.brsp.synthesisengine.utils;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class GraphUtils {

    public static List<Map<String, Object>> normalizeJsonToMapList(Object jsonSource) throws JsonProcessingException {
        List<Map<String, Object>> normalizedMapList = new ConcurrentLinkedList<>();
        if (jsonSource instanceof Map<?, ?> mapSource) {
            Object nodesObj = mapSource.get("nodes");
            if (nodesObj instanceof List<?> nodesList) {
                for (Map<String, Object> node : (List<Map<String, Object>>) nodesList) {
                    node.put("type", "node");
                    normalizedMapList.add(node);
                }
            }

            Object relsObj = mapSource.get("relationships");
            if (relsObj instanceof List<?> relsList) {
                for (Map<String, Object> relationship : (List<Map<String, Object>>) relsList) {
                    relationship.put("type", "relationship");
                    normalizedMapList.add(relationship);
                }
            }
        } else if (jsonSource instanceof Collection<?> collectionSource) {
            normalizedMapList.addAll((Collection<? extends Map<String, Object>>) collectionSource);
        } else {
            throw new IllegalArgumentException("Invalid JSON reference!");
        }
        return normalizedMapList;
    }

    public static ConcurrentLinkedList convertJsonToListCypherStatement(List<Map<String, Object>> jsonList) throws JsonProcessingException {
        var statements = new ConcurrentLinkedList<>();

        jsonList.forEach(it -> {
            String type = Utils.nvl(it.get("type"), "node").toString();

            if (Objects.equals(type, "node")) {
                var labels = new ConcurrentLinkedList<>();
                if (it.get("labels") instanceof Collection<?> labelsList) {
                    labels.addAll(labelsList);
                } else if (it.get("label") instanceof String labelString) {
                    labels.add(labelString);
                } else {
                    throw new IllegalArgumentException("Invalid neo4j label neither labels list!");
                }

                var skip = List.of("key", "type", "label", "labels", "properties");
                List<String> vals = new ConcurrentLinkedList<>();
                var properties = (Map<String, Object>) it.get("properties");
                properties.forEach((k, v) -> {
                    var textValue = JsonUtils.writeAsJsonStringCircular(v, true, false);
                    vals.add("n." + k + " = " + textValue);
                });

                it.forEach((k, v) -> {
                    if (!skip.contains(k)) {
                        var textValue = JsonUtils.writeAsJsonStringCircular(v, true, false);
                        vals.add("n." + k + " = " + textValue);
                    }
                });

                var valsString = String.join(",\n", vals);

                String labelString = labels.stream()
                        .map(label -> ":" + label)
                        .collect(Collectors.joining());

                String statement = """
                                                MERGE (n$label {key: "$key"})
                                                ON CREATE SET
                                                  $vals
                                                ON MATCH SET
                                                  $vals
                                                RETURN n
                                                """.replace("$label", labelString)
                        .replace("$key", (String) it.get("key"))
                        .replace("$vals", valsString);

                statements.add(Map.of("statement", statement.replace("'", "§§§").replace("\"", "'").replace("§§§", "\\\"")));
            } else if (Objects.equals(type, "relationship")) {
                String relType = (String) it.get("label");
                if (relType == null)
                    throw new IllegalArgumentException("Relationship label is missing!");

                String startKey = (String) it.get("startKey");
                String endKey = (String) it.get("endKey");

                if (startKey == null || endKey == null) {
                    throw new IllegalArgumentException("Relationship start or end key is missing!");
                }

                List<String> props = new ConcurrentLinkedList<>();
                Map<String, Object> properties = (Map<String, Object>) it.get("properties");

                if (properties != null) {
                    properties.forEach((k, v) -> {
                        var textValue = JsonUtils.writeAsJsonStringCircular(v, true, false);
                        props.add("r." + k + " = " + textValue);
                    });
                }

                String propString = String.join(",\n", props);

                String statement = """
                                                MATCH (a {key: "$startKey"})
                                                MATCH (b {key: "$endKey"})
                                                MERGE (a)-[r:$relType]->(b)
                                                """.replace("$startKey", startKey)
                        .replace("$endKey", endKey)
                        .replace("$relType", relType);
                if (!propString.isBlank()) {
                    statement += """
                                                    
                                                    ON CREATE SET
                                                      $props
                                                    ON MATCH SET
                                                      $props
                                                    RETURN a,b
                                                    """.replace("$props", propString);
                } else {
                    statement += "RETURN r";
                }
                statements.add(Map.of("statement", statement.replace("'", "§§§").replace("\"", "'").replace("§§§", "\\\"")));
            } else {
                throw new IllegalArgumentException("Invalid format JSON item type: " + type);
            }
        });
        return statements;
    }

    public static String extractLabelsByStatements(ConcurrentLinkedList<Object> statements) {
        Set<String> vlabels = new LinkedHashSet<>();
        Set<String> elabels = new LinkedHashSet<>();

        Pattern labelAnywhere = Pattern.compile("(?<!\\[):(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_]*))");
        Pattern relTypes = Pattern.compile("\\[\\s*(?:`[^`]+`|[A-Za-z_][A-Za-z0-9_]*)?\\s*:(?:`([^`]+)`|([A-Za-z_][A-Za-z0-9_]*))");

        statements.forEach(s -> {
            if (s instanceof Map<?, ?> map) {
                Object statementObj = map.get("statement");
                if (statementObj instanceof String cypherQuery) {
                    Matcher m1 = labelAnywhere.matcher(cypherQuery);
                    while (m1.find()) {
                        String name = m1.group(1) != null ? m1.group(1) : m1.group(2);
                        vlabels.add(name);
                    }
                    Matcher m2 = relTypes.matcher(cypherQuery);
                    while (m2.find()) {
                        String name = m2.group(1) != null ? m2.group(1) : m2.group(2);
                        elabels.add(name);
                    }
                }
            }
        });

        vlabels.removeAll(elabels);

        StringBuilder sb = new StringBuilder();

        vlabels.forEach(v -> sb.append("CREATE VLABEL IF NOT EXISTS ").append(formatIdentifier(v)).append(";\n"));

        elabels.forEach(e -> sb.append("CREATE ELABEL IF NOT EXISTS ").append(formatIdentifier(e)).append(";\n"));

        return sb.toString();
    }

    private static String formatIdentifier(String name) {
        if (name.matches("[A-Za-z_][A-Za-z0-9_]*")) return name;
        return "`" + name.replace("`", "``") + "`";
    }


}
