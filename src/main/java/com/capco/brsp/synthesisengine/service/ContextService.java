package com.capco.brsp.synthesisengine.service;

import com.capco.brsp.synthesisengine.flow.Flow;
import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.capco.brsp.synthesisengine.utils.Utils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service("contextService")
public class ContextService {
    private static final ConcurrentLinkedHashMap<String, ConcurrentLinkedHashMap<String, Object>> CONTEXT = new ConcurrentLinkedHashMap<>();

    private static final ThreadLocal<String> THREAD_FLOW_CONTEXT_MAPPING = new ThreadLocal<>();

    public String getFlowKey() {
        return THREAD_FLOW_CONTEXT_MAPPING.get();
    }

    public String setFlowKey(String flowKey) {
        THREAD_FLOW_CONTEXT_MAPPING.set(flowKey);

        return flowKey;
    }

    public String setFlowKey(UUID projectUUID, String contextKey) {
        var flowKey = Utils.combinedKey(projectUUID, contextKey);
        return this.setFlowKey(flowKey);
    }

    public ConcurrentLinkedHashMap<String, Object> setTempContext(String flowKey, Map<String, Object> context) {
        this.setFlowKey(flowKey);

        ConcurrentLinkedHashMap<String, Object> tempContext = new ConcurrentLinkedHashMap<>(context);
        CONTEXT.put(flowKey, tempContext);

        return tempContext;
    }

    private ConcurrentLinkedHashMap<String, Object> startNewProjectContextKey(String flowKey) {
        this.setFlowKey(flowKey);

        CONTEXT.put(flowKey, new ConcurrentLinkedHashMap<>());

        return CONTEXT.get(flowKey);
    }

    public void loadProjectContext(String flowKey, ConcurrentLinkedHashMap<String, Object> projectContext) {
        this.setFlowKey(flowKey);

        CONTEXT.put(flowKey, projectContext);
    }

    public ConcurrentLinkedHashMap<String, Object> startNewProjectContextKey(UUID projectUUID, String contextKey) {
        var flowKey = this.setFlowKey(projectUUID, contextKey);

        var projectContext = this.startNewProjectContextKey(flowKey);
        projectContext.put("projectUUID", projectUUID);

        return projectContext;
    }

    public ConcurrentLinkedHashMap<String, Object> getProjectContext() {
        var flowKey = getFlowKey();
        return CONTEXT.get(flowKey);
    }

    public Flow getFlow(String flowKey) {
        return Flow.PROJECT_FLOW.get(flowKey);
    }

    public Flow getCurrentFlow() {
        var flowKey = getFlowKey();
        return getFlow(flowKey);
    }

    public void clear() {
        String flowKey = getFlowKey();
        CONTEXT.remove(flowKey);
        THREAD_FLOW_CONTEXT_MAPPING.remove();
    }
}
