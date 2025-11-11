package com.capco.brsp.synthesisengine.mcp;

import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.mcp.dto.MonolithInputsDto;
import com.capco.brsp.synthesisengine.mcp.dto.MonolithPromptDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * High-level facade to prepare inputs and produce a ready-to-use prompt for the MCP monolith decomposition.
 * Usage pattern:
 *  - Call prepare(...) to save files and get URLs
 *  - Use the suggested prompt with an Agent configured to allow the monolith MCP tool
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonolithDecompositionFacade {

    private final MonolithDecompositionMcpService monolithService;

    /**
     * Prepare inputs and build a prompt with an internally generated uid (compatibilidade).
     */
    public MonolithPromptDto prepare(String dataMatrixCsv,
                                     String adjacencyMatrixCsv,
                                     String graphPerformsCallsSvg) throws IOException {
        MonolithInputsDto inputs = monolithService.prepareInputs(dataMatrixCsv, adjacencyMatrixCsv, graphPerformsCallsSvg);
        String uid = UUID.randomUUID().toString();
        String prompt = monolithService.buildMonolithPrompt(inputs, uid);
        return MonolithPromptDto.builder()
                .inputs(inputs)
                .prompt(prompt)
                .build();
    }

    /**
     * Prepare inputs and build a prompt using the provided uid.
     */
    public MonolithPromptDto prepare(String dataMatrixCsv,
                                     String adjacencyMatrixCsv,
                                     String graphPerformsCallsSvg,
                                     String uid) throws IOException {
        MonolithInputsDto inputs = monolithService.prepareInputs(dataMatrixCsv, adjacencyMatrixCsv, graphPerformsCallsSvg);
        String prompt = monolithService.buildMonolithPrompt(inputs, uid);
        return MonolithPromptDto.builder()
                .inputs(inputs)
                .prompt(prompt)
                .build();
    }

    /**
     * Optional helper to adjust AgentDto tools allowlist to include only MCP monolith tools if needed.
     * This ensures the LLM is more likely to call the right tool.
     */
    public AgentDto withMonolithTools(AgentDto agent, List<String> monolithToolNames) {
        if (agent == null) return null;
        agent.setTools(monolithToolNames);
        return agent;
    }
}
