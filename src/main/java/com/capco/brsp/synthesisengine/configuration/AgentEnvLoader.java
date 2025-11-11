package com.capco.brsp.synthesisengine.configuration;


import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.utils.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component(value = "agentEnvLoader")
public class AgentEnvLoader {
    private static final Pattern AGENT_KEY = Pattern.compile(
            "^AGENT_([A-Z0-9_]+)_(DEPLOYMENT_NAME|EMBEDDING_MODEL|SYSTEM_INSTRUCTIONS|FREQUENCY_PENALTY|PRESENCE_PENALTY|STOP_SEQUENCES|MAX_TOKENS|MAX_TURNS|IS_EMBEDDING|TEMPERATURE|TOP_P|TOP_K|PROVIDER|METADATA_JSON|TOOLS|MODEL|BEFORE|AFTER|NAME)$"
    );

    private final Environment env;


    public AgentEnvLoader(Environment env) {
        this.env = env;
    }

    public List<AgentDto> loadFromEnv() {
        Map<String, String> merged = new LinkedHashMap<>();

        merged.putAll(System.getenv());

        try {
            for (PropertySource<?> ps : ((AbstractEnvironment) env).getPropertySources()) {
                if (ps instanceof EnumerablePropertySource<?> eps) {
                    for (String name : eps.getPropertyNames()) {
                        if (name != null && name.startsWith("AGENT_")) {
                            Object v = eps.getProperty(name);
                            if (v != null) merged.putIfAbsent(name, String.valueOf(v));
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        try {
            Path envPath = Path.of(System.getProperty("user.dir"), ".env");
            if (Files.exists(envPath)) {
                for (String line : Files.readAllLines(envPath)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int idx = trimmed.indexOf('=');
                    if (idx > 0) {
                        String key = trimmed.substring(0, idx).trim();
                        String value = trimmed.substring(idx + 1).trim();
                        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        value = Pattern.compile("\\$\\{([^}]+)}").matcher(value).replaceAll(m -> {
                            String k = m.group(1);
                            String repl = merged.getOrDefault(k, System.getenv(k));
                            return repl != null ? repl : "";
                        });
                        merged.put(key, value);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn(".env not loaded: {}", ex.getMessage());
        }

        Map<String, Map<String, String>> byAlias = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("AGENT_")) continue;

            String field = null;
            String alias = null;
            for (String suffix : FIELD_SUFFIXES) {
                String end = "_" + suffix;
                if (key.endsWith(end)) {
                    field = suffix;
                    alias = key.substring("AGENT_".length(), key.length() - end.length());
                    break;
                }
            }
            if (field == null || alias == null || alias.isBlank()) continue; // not an agent key we recognize

            byAlias.computeIfAbsent(alias, k -> new LinkedHashMap<>()).put(field, entry.getValue());
        }

        List<AgentDto> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : byAlias.entrySet()) {
            AgentDto dto = buildAgentFromEnv(entry.getKey(), entry.getValue());
            if (dto != null) result.add(dto);
        }

        if (result.isEmpty()) {
            log.warn("No AGENT_*_* variables found in environment");
        } else {
            log.info("Loaded {} agent(s) from environment: {}", result.size(), result.stream().map(AgentDto::getName).toList() );
        }

        return result;
    }

    private AgentDto buildAgentFromEnv(String alias, Map<String, String> env) {
        AgentDto agent = new AgentDto();
        agent.setName(get(env, "NAME", alias));
        agent.setProvider(get(env, "PROVIDER", null));
        agent.setModel(get(env, "MODEL", null));
        agent.setDeploymentName(get(env, "DEPLOYMENT_NAME", null));
        agent.setEmbeddingModel(get(env, "EMBEDDING_MODEL", null));
        agent.setSystemInstructions(get(env, "SYSTEM_INSTRUCTIONS", null));
        agent.setBefore(get(env, "BEFORE", null));
        agent.setAfter(get(env, "AFTER", null));
        agent.setTemperature(parseDouble(get(env, "TEMPERATURE", null), 0.7));
        agent.setMaxTokens(parseInt(get(env, "MAX_TOKENS", null), 4000));
        agent.setTopP(parseDouble(get(env, "TOP_P", null), 1.0));
        agent.setFrequencyPenalty(parseDouble(get(env, "FREQUENCY_PENALTY", null), 0.0));
        agent.setPresencePenalty(parseDouble(get(env, "PRESENCE_PENALTY", null), 0.0));
        agent.setTopK(parseInt(get(env, "TOP_K", null), 0));
        agent.setMaxTurns(parseInt(get(env, "MAX_TURNS", null), 50));
        agent.setRetry(parseInt(get(env, "RETRY", null), 3));
        agent.setIsEmbedding(parseBool(get(env, "IS_EMBEDDING", null), false));

        String toolsCsv = get(env, "TOOLS", null);
        agent.setTools(toolsCsv == null || toolsCsv.isBlank() ? null : Arrays.stream(toolsCsv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());

        String stopsCsv = get(env, "STOP_SEQUENCES", null);
        agent.setStopSequences(stopsCsv != null && !stopsCsv.isBlank() ? stopsCsv : null);

        String metadataJson = get(env, "METADATA_JSON", null);
        if (metadataJson != null && !metadataJson.isBlank()) {
            try {
                Map<String, Object> map = (Map<String, Object>) JsonUtils.readAsObject(metadataJson, Map.class);
                agent.setMetadata(map);
            } catch (Exception ex) {
                log.warn("Invalid METADATA_JSON for agent {}: {}", alias, ex.getMessage());
            }
        }
        return agent;
    }

    private String get(Map<String, String> vals, String key, String def) {
        String v = vals.get(key);
        if (v == null || v.isBlank()) return def;
        if ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'"))) {
            v = v.substring(1, v.length() - 1);
        }
        return v;
    }

    private static Integer parseInt(String s, Integer def) { try { return s != null ? Integer.parseInt(s.trim()) : def; } catch (Exception ignored) { return def; } }
    private static Double parseDouble(String s, Double def) { try { return s != null ? Double.parseDouble(s.trim()) : def; } catch (Exception ignored) { return def; } }
    private static Boolean parseBool(String s, Boolean def) { try { return s != null ? Boolean.parseBoolean(s.trim()) : def; } catch (Exception ignored) { return def; } }

    private static final List<String> FIELD_SUFFIXES = List.of(
            "DEPLOYMENT_NAME",
            "EMBEDDING_MODEL",
            "SYSTEM_INSTRUCTIONS",
            "FREQUENCY_PENALTY",
            "PRESENCE_PENALTY",
            "STOP_SEQUENCES",
            "MAX_TOKENS",
            "MAX_TURNS",
            "IS_EMBEDDING",
            "TEMPERATURE",
            "TOP_P",
            "TOP_K",
            "PROVIDER",
            "METADATA_JSON",
            "TOOLS",
            "MODEL",
            "BEFORE",
            "AFTER",
            "NAME"
    );
}