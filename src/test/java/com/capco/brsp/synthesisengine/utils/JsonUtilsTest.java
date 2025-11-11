package com.capco.brsp.synthesisengine.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class JsonUtilsTest {

    @Test
    void testIsJsonSchemaValid_withValidJsonAndSchemaa() throws JsonProcessingException {
        String content = """
                {
                    "Post-migration application management support & People Readiness (Hypercare)": {
                        "why": {
                            "introduction": "Post-migration application management and people readiness are critical to maintaining system stability and empowering teams to manage the new cloud environment effectively, especially during the hypercare phase.",
                            "bullets": []
                        },
                        "subDimension": {
                            "Hypercare Operational Support": {
                                "description": "Provide hands-on support after migration to address operational issues, ensure system stability, and handle incidents effectively.",
                                "how": "Set up a dedicated hypercare team to monitor applications in the cloud, establish KPIs for measuring stability, and implement a streamlined incident management process.",
                                "withWhom": "Cloud operations specialists, support engineers, business process owners, and end-users experiencing issues.",
                                "questions": {
                                    "immediateSupportQuery": {
                                        "question": "What are the priority issues to resolve during hypercare?",
                                        "deliverable": "A list of critical incidents addressed along with their resolutions and time taken for fixes."
                                    },
                                    "stabilizationMetricsQuery": {
                                        "question": "How can we measure application stability during hypercare?",
                                        "deliverable": "A KPI dashboard tracking metrics like system uptime, response times, and incident resolution rates."
                                    }
                                }
                            },
                            "People Readiness & Enablement": {
                                "description": "Equip teams with the necessary tools, training, and documentation to manage the migrated applications effectively.",
                                "how": "Conduct training sessions, create detailed user guides, and establish knowledge transfer processes to upskill team members for managing cloud-based applications.",
                                "withWhom": "Training specialists, cloud migration leads, key business users, and team managers.",
                                "questions": {
                                    "knowledgeTransferQuery": {
                                        "question": "What materials are required for effective knowledge transfer?",
                                        "deliverable": "Comprehensive training materials, operational guides, and technical documentation tailored to user roles."
                                    },
                                    "teamEnablementQuery": {
                                        "question": "How can we assess team readiness post-training?",
                                        "deliverable": "A readiness assessment framework including feedback surveys, skill tests, and performance evaluations."
                                    }
                                }
                            }
                        }
                    },
                    "Another Post-migration application management support & People Readiness (Hypercare)": {
                        "objective": "Ensure smooth operation of migrated applications on the cloud and equip teams with the necessary knowledge, tools, and support to sustain and optimize the new environment.",
                        "why": {
                            "introduction": "Post-migration application management and people readiness are critical to maintaining system stability and empowering teams to manage the new cloud environment effectively, especially during the hypercare phase.",
                            "bullets": [
                                "Hypercare support minimizes operational disruptions during the stabilization period post-migration.",
                                "Ready teams are essential to maintain, monitor, and optimize cloud-based applications.",
                                "Knowledge transfer ensures business continuity and addresses potential skills gaps introduced by the transition."
                            ]
                        },
                        "subDimension": {
                            "Hypercare Operational Support": {
                                "description": "Provide hands-on support after migration to address operational issues, ensure system stability, and handle incidents effectively.",
                                "how": "Set up a dedicated hypercare team to monitor applications in the cloud, establish KPIs for measuring stability, and implement a streamlined incident management process.",
                                "withWhom": "Cloud operations specialists, support engineers, business process owners, and end-users experiencing issues.",
                                "questions": {
                                    "immediateSupportQuery": {
                                        "question": "What are the priority issues to resolve during hypercare?",
                                        "deliverable": "A list of critical incidents addressed along with their resolutions and time taken for fixes."
                                    },
                                    "stabilizationMetricsQuery": {
                                        "question": "How can we measure application stability during hypercare?",
                                        "deliverable": "A KPI dashboard tracking metrics like system uptime, response times, and incident resolution rates."
                                    }
                                }
                            },
                            "People Readiness & Enablement": {
                                "description": "Equip teams with the necessary tools, training, and documentation to manage the migrated applications effectively.",
                                "how": "Conduct training sessions, create detailed user guides, and establish knowledge transfer processes to upskill team members for managing cloud-based applications.",
                                "withWhom": "Training specialists, cloud migration leads, key business users, and team managers.",
                                "questions": {
                                    "knowledgeTransferQuery": {
                                        "question": "What materials are required for effective knowledge transfer?",
                                        "deliverable": "Comprehensive training materials, operational guides, and technical documentation tailored to user roles."
                                    },
                                    "teamEnablementQuery": {
                                        "question": "How can we assess team readiness post-training?",
                                        "deliverable": "A readiness assessment framework including feedback surveys, skill tests, and performance evaluations."
                                    }
                                }
                            }
                        }
                    }
                }
                """;
        String schema = """
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "patternProperties": {
                    "^.*$": {
                      "type": "object",
                      "required": ["objective", "why", "subDimension"],
                      "properties": {
                        "objective": { "type": "string" },
                        "why": {
                          "type": "object",
                          "required": ["introduction", "bullets"],
                          "properties": {
                            "introduction": { "type": "string" },
                            "bullets": {
                              "type": "array",
                              "minItems": 1,
                              "items": { "type": "string" }
                            }
                          },
                          "additionalProperties": false
                        },
                        "subDimension": {
                          "type": "object",
                          "patternProperties": {
                            "^.*$": {
                              "type": "object",
                              "required": ["description", "how", "withWhom", "questions"],
                              "properties": {
                                "description": { "type": "string" },
                                "how": { "type": "string" },
                                "withWhom": { "type": "string" },
                                "questions": {
                                  "type": "object",
                                  "patternProperties": {
                                    "^.*$": {
                                      "type": "object",
                                      "required": ["question", "deliverable"],
                                      "properties": {
                                        "question": { "type": "string" },
                                        "deliverable": { "type": "string" }
                                      },
                                      "additionalProperties": false
                                    }
                                  },
                                  "additionalProperties": false
                                }
                              },
                              "additionalProperties": false
                            }
                          },
                          "additionalProperties": false
                        }
                      },
                      "additionalProperties": false
                    }
                  },
                  "additionalProperties": false
                }
                """;
        var errors = JsonUtils.getErrorsAgainstJsonSchema(content, schema);
        log.error("{}", errors);
    }

//    @Test
//    void testIsJsonSchemaValid_withValidJsonAndSchema() {
//        String content = "{\"name\":\"John\",\"age\":30}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertTrue(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withInvalidJsonAgainstSchema() {
//        String content = "{\"name\":123,\"age\":\"thirty\"}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withMissingRequiredField() {
//        String content = "{\"name\":\"John\"}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withSchemaField() {
//        String content = "{\"name\":\"John\",\"age\":30}";
//        String schema = """
//                {
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertTrue(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withContentAsObject() {
//        class Person {
//            public final String name = "John";
//            public final int age = 30;
//        }
//        Person person = new Person();
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertTrue(JsonUtils.isJsonSchemaValid(person, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withInvalidJsonString() {
//        String content = "{invalid json}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object"
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testIsJsonSchemaValid_withInvalidSchemaString() {
//        String content = "{\"name\":\"John\"}";
//        String schema = "{invalid schema}";
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testValidJsonAndSchema() {
//        String content = "{\"name\":\"John\",\"age\":30}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertTrue(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//
//    @Test
//    void testMissingRequiredField() {
//        String content = "{\"name\":\"John\"}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testContentAsObject() {
//        class Person {
//            public final String name = "John";
//            public final int age = 30;
//        }
//        Person person = new Person();
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object",
//                  "properties": {
//                    "name": {"type": "string"},
//                    "age": {"type": "integer"}
//                  },
//                  "required": ["name", "age"]
//                }
//                """;
//        assertTrue(JsonUtils.isJsonSchemaValid(person, schema));
//    }
//
//    @Test
//    void testInvalidJsonString() {
//        String content = "{invalid json}";
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object"
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testInvalidSchemaString() {
//        String content = "{\"name\":\"John\"}";
//        String schema = "{invalid schema}";
//        assertFalse(JsonUtils.isJsonSchemaValid(content, schema));
//    }
//
//    @Test
//    void testNullContent() {
//        String schema = """
//                {
//                  "$schema": "http://json-schema.org/draft-07/schema#",
//                  "type": "object"
//                }
//                """;
//        assertFalse(JsonUtils.isJsonSchemaValid(null, schema));
//    }
//
//    @Test
//    void testNullSchema() {
//        String content = "{\"name\":\"John\"}";
//        assertFalse(JsonUtils.isJsonSchemaValid(content, null));
//    }


    @Test
    @DisplayName("default keyword takes precedence")
    void testDefaultKeyword() throws JsonProcessingException {
        String schema = """
                { "default": 123, "type": "integer" }
                """;
        JsonNode sample = JsonUtils.generateSample(schema);
        assertTrue(sample.isInt());
        assertEquals(123, sample.intValue());
    }

    @Test
    @DisplayName("enum keyword yields first element")
    void testEnumKeyword() throws JsonProcessingException {
        String schema = """
                { "enum": ["red", "green", "blue"], "type": "string" }
                """;
        JsonNode sample = JsonUtils.generateSample(schema);
        assertTrue(sample.isTextual());
        assertEquals("red", sample.textValue());
    }

    @Test
    @DisplayName("string type with no format")
    void testStringType() throws JsonProcessingException {
        String schema = """
                { "type": "string" }
                """;
        JsonNode sample = JsonUtils.generateSample(schema);
        assertTrue(sample.isTextual());
        assertEquals("string", sample.textValue());
    }

    @Test
    @DisplayName("string formats: date, date-time, email")
    void testStringFormats() throws JsonProcessingException {
        String dateSchema = """
                { "type": "string", "format": "date" }
                """;
        JsonNode d = JsonUtils.generateSample(dateSchema);
        assertEquals("1970-01-01", d.textValue());

        String dateTimeSchema = """
                { "type": "string", "format": "date-time" }
                """;
        JsonNode dt = JsonUtils.generateSample(dateTimeSchema);
        assertEquals("1970-01-01T00:00:00Z", dt.textValue());

        String emailSchema = """
                { "type": "string", "format": "email" }
                """;
        JsonNode e = JsonUtils.generateSample(emailSchema);
        assertEquals("user@example.com", e.textValue());
    }

    @Test
    @DisplayName("integer and number with minimum")
    void testNumericTypesWithMinimum() throws JsonProcessingException {
        String intSchema = """
                { "type": "integer", "minimum": 50 }
                """;
        JsonNode i = JsonUtils.generateSample(intSchema);
        assertTrue(i.isInt());
        assertEquals(50, i.intValue());

        String numSchema = """
                { "type": "number", "minimum": 3.14 }
                """;
        JsonNode n = JsonUtils.generateSample(numSchema);
        assertTrue(n.isDouble());
        assertEquals(3.14, n.doubleValue());
    }

    @Test
    @DisplayName("boolean and null types")
    void testBooleanAndNull() throws JsonProcessingException {
        String boolSchema = """
                { "type": "boolean" }
                """;
        JsonNode b = JsonUtils.generateSample(boolSchema);
        assertTrue(b.isBoolean());
        assertTrue(b.booleanValue());

        String nullSchema = """
                { "type": "null" }
                """;
        JsonNode z = JsonUtils.generateSample(nullSchema);
        assertTrue(z.isNull());
    }

    @Test
    @DisplayName("array with single‐item schema")
    void testArrayType() throws JsonProcessingException {
        String schema = """
                { "type": "array", "items": { "type": "string" } }
                """;
        JsonNode arr = JsonUtils.generateSample(schema);
        assertTrue(arr.isArray());
        assertEquals(1, arr.size());
        assertEquals("string", arr.get(0).textValue());
    }

    @Test
    @DisplayName("object with nested properties")
    void testObjectType() throws JsonProcessingException {
        String schema = """
            {
              "type": "object",
              "properties": {
                "id":    { "type": "integer" },
                "name":  { "type": "string" },
                "child": {
                  "type": "object",
                  "properties": {
                    "flag": { "type": "boolean" }
                  },
                  "required": ["flag"]
                }
              },
              "required": ["id", "name", "child"]
            }
            """;
        JsonNode obj = JsonUtils.generateSample(schema);
        assertTrue(obj.isObject());
        assertTrue(obj.has("id") && obj.get("id").isInt());
        assertTrue(obj.has("name") && obj.get("name").isTextual());
        assertTrue(obj.has("child") && obj.get("child").isObject());
        JsonNode child = obj.get("child");
        assertTrue(child.has("flag") && child.get("flag").isBoolean());
    }

    @Test
    @DisplayName("fallback object for unknown type")
    void testUnknownTypeFallback() throws JsonProcessingException {
        String schema = """
                { "type": "weirdType" }
                """;
        JsonNode obj = JsonUtils.generateSample(schema);
        assertTrue(obj.isObject());
        assertEquals(0, obj.size(), "fallback should be an empty object");
    }
}