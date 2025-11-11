package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.dto.AgentDto;
import com.capco.brsp.synthesisengine.dto.ConversationMemoryDto;
import com.capco.brsp.synthesisengine.dto.MemoryMessageDto;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.List;

public interface ILLMSpringService {
    String prompt(String prompt, AgentDto config);
    String prompt(String prompt, AgentDto config, String conversationId);
    ChatResponse callWithConfig(String prompt, AgentDto config);
    ChatResponse callWithConfig(String prompt, AgentDto config, String conversationId);
    void startChat();
    void startChat(String conversationId);
    void endChat();
    void endChat(String conversationId);
    void clearTemporaryLLMConfig();
    List<MemoryMessageDto> getCurrentConversationHistory();
    List<MemoryMessageDto> getConversationHistory(String conversationId);
    void analyzeConversationImportance(AgentDto config);
    void analyzeConversationImportance(AgentDto config, String conversationId);
    ConversationMemoryDto getOrCreateConversationMemory(String conversationId);
}
