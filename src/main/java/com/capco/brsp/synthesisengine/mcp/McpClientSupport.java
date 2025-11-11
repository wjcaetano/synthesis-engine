package com.capco.brsp.synthesisengine.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Thin support wrapper around Spring AI's MCP tool callback provider.
 * This allows other services to depend on a simple MCP-facing bean,
 * while the actual ChatClient/tool invocation stays in LLMSpringService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientSupport {

    @Autowired(required = false)
    private SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    /**
     * Verifies MCP connectivity by checking if we have any tool callbacks loaded.
     */
    public boolean isAvailable() {
        try {
            var tools = mcpToolCallbackProvider.getToolCallbacks();
            boolean ok = tools != null && tools.length > 0;
            if (!ok) {
                log.warn("MCP is configured but no tools were discovered.");
            }
            return ok;
        } catch (Exception ex) {
            log.warn("MCP availability check failed: {}", ex.getMessage());
            return false;
        }
    }
}
