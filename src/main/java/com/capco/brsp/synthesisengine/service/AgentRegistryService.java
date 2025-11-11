package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.configuration.AgentEnvLoader;
import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.exception.LLMConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service(value = "agentRegistryService")
public class AgentRegistryService {
    private final AgentEnvLoader agentEnvLoader;
    private final Map<String, AgentDto> byName = new LinkedHashMap<>();

    public AgentRegistryService(@Qualifier(value = "agentEnvLoader") AgentEnvLoader agentEnvLoader) {
        this.agentEnvLoader = agentEnvLoader;
    }

    @PostConstruct
    public void init() {
        List<AgentDto> agents = agentEnvLoader.loadFromEnv();
        for (AgentDto agent : agents) {
            try {
                validate(agent);
                String key = agent.getName() != null ? agent.getName().trim().toUpperCase(Locale.ROOT) : UUID.randomUUID().toString();
                byName.put(key, agent);
                log.info("Registered agent {} with key {}", agent.getName(), key);
            } catch (Exception ex) {
                log.warn("Skipping agent '{}' due to validation error: {}", agent.getName(), ex.getMessage());
            }
        }

        if (byName.isEmpty()) {
            log.warn("AgentRegistry has no agents from environment.");
        }
    }

    public Optional<AgentDto> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(byName.get(name.trim().toUpperCase(Locale.ROOT)));
    }

    public Collection<AgentDto> all() {
        return byName.values();
    }

    public AgentDto require(String name) {
        return find(name).orElseThrow(() -> new LLMConfigurationException("Agent not found: " + name, "registry"));
    }

    public void validate(AgentDto agent) {
        if (agent == null) throw new IllegalArgumentException("Agent cannot be null");
        if (agent.getProvider() == null || agent.getProvider().isBlank()) {
            throw new IllegalArgumentException("provider is required");
        }
        String provider = agent.getProvider().toLowerCase(Locale.ROOT);
        switch (provider) {
            case "azure" -> {
                if (isBlank(agent.getDeploymentName()))
                    throw new IllegalArgumentException("Azure provider requires deployment name");
            }
            case "openai", "bedrock", "vertexai" -> {
                if (isBlank(agent.getModel()))
                    throw new IllegalArgumentException(provider + " provider requires model");
            }
            default -> throw new IllegalArgumentException("Unsupported provider: " + agent.getProvider());
        }
        if (agent.getTemperature() != null && (agent.getTemperature() < 0.0 || agent.getTemperature() > 2.0)) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (agent.getMaxTokens() != null && agent.getMaxTokens() <= 0) {
            throw new IllegalArgumentException("maxTokens must be greater than 0");
        }
        if (agent.getTopP() != null && (agent.getTopP() < 0.0 || agent.getTopP() > 1.0)) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0");
        }
        if (agent.getRetry() != null && agent.getRetry() < 0) {
            throw new IllegalArgumentException("retry must be >= 0");
        }
    }

    public AgentDto merge(AgentDto base, AgentDto override) {
        if (base == null) return override;
        if (override == null) return base;
        AgentDto out = new AgentDto();
        out.setName(nonNull(override.getName(), base.getName()));
        out.setProvider(nonNull(override.getProvider(), base.getProvider()));
        out.setModel(nonNull(override.getModel(), base.getModel()));
        out.setDeploymentName(nonNull(override.getDeploymentName(), base.getDeploymentName()));
        out.setEmbeddingModel(nonNull(override.getEmbeddingModel(), base.getEmbeddingModel()));
        out.setSystemInstructions(nonNull(override.getSystemInstructions(), base.getSystemInstructions()));
        out.setBefore(nonNull(override.getBefore(), base.getBefore()));
        out.setAfter(nonNull(override.getAfter(), base.getAfter()));
        out.setTemperature(nonNull(override.getTemperature(), base.getTemperature()));
        out.setMaxTokens(nonNull(override.getMaxTokens(), base.getMaxTokens()));
        out.setTopP(nonNull(override.getTopP(), base.getTopP()));
        out.setFrequencyPenalty(nonNull(override.getFrequencyPenalty(), base.getFrequencyPenalty()));
        out.setPresencePenalty(nonNull(override.getPresencePenalty(), base.getPresencePenalty()));
        out.setTopK(nonNull(override.getTopK(), base.getTopK()));
        out.setMaxTurns(nonNull(override.getMaxTurns(), base.getMaxTurns()));
        out.setRetry(nonNull(override.getRetry(), base.getRetry()));
        out.setIsEmbedding(nonNull(override.getIsEmbedding(), base.getIsEmbedding()));
        out.setTools(nonNull(override.getTools(), base.getTools()));
        out.setStopSequences(nonNull(override.getStopSequences(), base.getStopSequences()));
        out.setMetadata(nonNull(override.getMetadata(), base.getMetadata()));
        return out;
    }

    public void registerOrMerge(AgentDto candidate) {
        if (candidate == null) return;
        String key = candidate.getName() != null ? candidate.getName().trim().toUpperCase(Locale.ROOT) : null;
        AgentDto base = (key != null) ? byName.get(key) : null;

        AgentDto merged = merge(base, candidate);
        validate(merged);

        if (key == null) {
            key = merged.getName() != null ? merged.getName().trim().toUpperCase(Locale.ROOT) : UUID.randomUUID().toString();
        }
        byName.put(key, merged);
        log.info("Registered or merged agent {} with key {}", merged.getName(), key);
    }

    public void replace(String name, AgentDto agent) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required for replace");
        validate(agent);
        byName.put(name.trim().toUpperCase(Locale.ROOT), agent);
        log.info("Replaced agent {} with key {}", agent.getName(), name);
    }

    private static <T> T nonNull(T v, T def) {
        return v != null ? v : def;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
