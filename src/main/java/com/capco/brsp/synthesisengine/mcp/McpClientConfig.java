package com.capco.brsp.synthesisengine.mcp;

import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "mcp.client.toolcallback", name = "enabled", havingValue = "true", matchIfMissing = false)
    public SyncMcpToolCallbackProvider syncMcpToolCallbackProvider() {
        return new SyncMcpToolCallbackProvider();
    }
}
