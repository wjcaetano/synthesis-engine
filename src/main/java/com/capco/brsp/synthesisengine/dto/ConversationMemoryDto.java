package com.capco.brsp.synthesisengine.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ConversationMemoryDto {
    private UUID conversationId = UUID.randomUUID();
    private List<MemoryMessageDto> activeMessages = new ArrayList<>();
    private List<MemoryMessageDto> archivedMessages = new ArrayList<>();
    private int totalTokenCount = 0;
    private int maxTokens = 60000;
    private Map<String, Object> metadata = new ConcurrentHashMap<>();
}
